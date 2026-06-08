import sys
import struct
import json
import asyncio
import websockets

def serialize_output_config(port: int, api_key: str) -> bytes:
    port_bytes = bytearray()
    p = port
    while True:
        b = p & 0x7F
        p >>= 7
        if p:
            port_bytes.append(b | 0x80)
        else:
            port_bytes.append(b)
            break
            
    key_bytes = api_key.encode('utf-8')
    
    buf = bytearray()
    buf.append(0x08)
    buf.extend(port_bytes)
    buf.append(0x12)
    buf.append(len(key_bytes))
    buf.extend(key_bytes)
    return bytes(buf)

async def handle_ws(websocket, path=None):
    # 1. Wait for InitializeConversationEvent
    async for raw_msg in websocket:
        msg = json.loads(raw_msg)
        if "config" in msg:
            break
            
    # 2. Wait for InputEvent
    async for raw_msg in websocket:
        msg = json.loads(raw_msg)
        if "userInput" in msg or "user_input" in msg:
            break

    # 3. Push a ToolCall to Java client asking to run "add(10, 20)"
    step1 = {
        "seqNum": 1,
        "toolCall": {
            "id": "call-123",
            "name": "add",
            "argumentsJson": '{"a": 10, "b": 20}'
        }
    }
    await websocket.send(json.dumps(step1))

    # 4. Wait for ToolResponse from Java client
    success = False
    async for raw_msg in websocket:
        msg = json.loads(raw_msg)
        if "toolResponse" in msg or "tool_response" in msg:
            tr = msg.get("toolResponse", msg.get("tool_response", {}))
            response_json = tr.get("responseJson", tr.get("response_json", "{}"))
            if tr.get("id") == "call-123" and "30" in response_json:
                success = True
            break

    # 5. Push response StepUpdate containing final result
    step2 = {
        "seqNum": 2,
        "stepUpdate": {
            "trajectoryId": "mock-traj",
            "stepIndex": 2,
            "state": "STATE_DONE",
            "source": "SOURCE_MODEL",
            "target": "TARGET_USER",
            "text": "Result is 30" if success else "Failed tool execution",
            "textDelta": "Result is 30" if success else "Failed tool execution"
        }
    }
    await websocket.send(json.dumps(step2))
    await asyncio.sleep(0.05)

    # 6. Push TrajectoryStateUpdate containing STATE_IDLE
    step3 = {
        "seqNum": 3,
        "trajectoryStateUpdate": {
            "trajectoryId": "mock-traj",
            "state": "STATE_IDLE"
        }
    }
    await websocket.send(json.dumps(step3))

    try:
        await websocket.wait_closed()
    except Exception:
        pass

async def main():
    # A. Read InputConfig (length-delimited)
    raw_len = sys.stdin.buffer.read(4)
    if not raw_len:
        sys.exit(1)
    length = struct.unpack("<I", raw_len)[0]
    sys.stdin.buffer.read(length) # consume config bytes

    # B. Start WebSocket server on ephemeral port
    server = await websockets.serve(handle_ws, "127.0.0.1", 0)
    port = server.sockets[0].getsockname()[1]

    # C. Write OutputConfig response (length-delimited)
    serialized = serialize_output_config(port, "mock-api-key")
    
    sys.stdout.buffer.write(struct.pack("<I", len(serialized)) + serialized)
    sys.stdout.buffer.flush()

    await server.wait_closed()

if __name__ == '__main__':
    asyncio.run(main())
