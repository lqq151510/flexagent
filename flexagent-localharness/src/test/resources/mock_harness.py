import sys
import struct
import json
import asyncio
import websockets
from google.protobuf import json_format

# Include Python SDK paths for pb imports
sys.path.insert(0, '/Users/liuyongze/Desktop/sdk/flexagent-sdk-python')

from google.antigravity.connections.local import localharness_pb2

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
    step1 = localharness_pb2.OutputEvent()
    step1.seq_num = 1
    step1.tool_call.id = "call-123"
    step1.tool_call.name = "add"
    step1.tool_call.arguments_json = '{"a": 10, "b": 20}'
    await websocket.send(json_format.MessageToJson(step1))

    # 4. Wait for ToolResponse from Java client
    success = False
    async for raw_msg in websocket:
        msg = json.loads(raw_msg)
        if "toolResponse" in msg or "tool_response" in msg:
            tr = msg.get("toolResponse", msg.get("tool_response", {}))
            response_json = tr.get("responseJson", tr.get("response_json", "{}"))
            # Expecting response payload containing "30"
            if tr.get("id") == "call-123" and "30" in response_json:
                success = True
            break

    # 5. Push response StepUpdate containing final result
    step2 = localharness_pb2.OutputEvent()
    step2.seq_num = 2
    step2.step_update.trajectory_id = "mock-traj"
    step2.step_update.step_index = 2
    step2.step_update.state = localharness_pb2.StepUpdate.State.STATE_DONE
    step2.step_update.source = localharness_pb2.StepUpdate.Source.SOURCE_MODEL
    step2.step_update.target = localharness_pb2.StepUpdate.Target.TARGET_USER
    if success:
        step2.step_update.text = "Result is 30"
        step2.step_update.text_delta = "Result is 30"
    else:
        step2.step_update.text = "Failed tool execution"
        step2.step_update.text_delta = "Failed tool execution"
    await websocket.send(json_format.MessageToJson(step2))
    await asyncio.sleep(0.05)

    # 6. Push TrajectoryStateUpdate containing STATE_IDLE
    step3 = localharness_pb2.OutputEvent()
    step3.seq_num = 3
    step3.trajectory_state_update.trajectory_id = "mock-traj"
    step3.trajectory_state_update.state = localharness_pb2.TrajectoryStateUpdate.State.STATE_IDLE
    await websocket.send(json_format.MessageToJson(step3))

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
    out_config = localharness_pb2.OutputConfig()
    out_config.port = port
    out_config.api_key = "mock-api-key"
    serialized = out_config.SerializeToString()
    
    sys.stdout.buffer.write(struct.pack("<I", len(serialized)) + serialized)
    sys.stdout.buffer.flush()

    await server.wait_closed()

if __name__ == '__main__':
    asyncio.run(main())
