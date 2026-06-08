import pytest
from flexagent.core.agent_memory import (
    InMemoryAgentMemory,
    SystemMessage,
    UserMessage,
    AssistantMessage
)
from flexagent.core.agent_runtime import AgentRuntime
from flexagent.core.agent_client import AgentClient

def test_in_memory_agent_memory():
    memory = InMemoryAgentMemory()
    session_id = "test_sess"
    assert len(memory.get_messages(session_id)) == 0

    sys_msg = SystemMessage(text="You are a helpful assistant.")
    memory.add_message(session_id, sys_msg)
    assert len(memory.get_messages(session_id)) == 1
    assert memory.get_messages(session_id)[0].role == "system"

    user_msg = UserMessage(text="Hello")
    memory.add_message(session_id, user_msg)
    assert len(memory.get_messages(session_id)) == 2

    ast_msg = AssistantMessage(text="Hi there!")
    memory.add_message(session_id, ast_msg)
    assert len(memory.get_messages(session_id)) == 3

    memory.clear(session_id)
    assert len(memory.get_messages(session_id)) == 0

class DummyRuntime(AgentRuntime[str]):
    def run(self, input_data: str) -> str:
        self._history_messages.append(UserMessage(text=input_data))
        response = f"Echo: {input_data}"
        self._history_messages.append(AssistantMessage(text=response))
        return response

    async def run_async(self, input_data: str) -> str:
        return self.run(input_data)

def test_agent_runtime():
    runtime = DummyRuntime()
    result = runtime.run("Test input")
    assert result == "Echo: Test input"
    
    messages = runtime.get_history_messages()
    assert len(messages) == 2
    assert messages[0].text == "Test input"
    assert messages[1].text == "Echo: Test input"

@pytest.mark.asyncio
async def test_agent_runtime_async():
    runtime = DummyRuntime()
    result = await runtime.run_async("Test async input")
    assert result == "Echo: Test async input"
    messages = runtime.get_history_messages()
    assert len(messages) == 2

def test_agent_client():
    memory = InMemoryAgentMemory()
    runtime = DummyRuntime()
    client = AgentClient(active_runtime=runtime, memory=memory)
    
    msg = client.generate("Hello", session_id="sess1")
    assert msg.text == "Echo: Hello"
    
    # test memory has been updated
    msgs = memory.get_messages("sess1")
    assert len(msgs) == 2
    assert msgs[0].text == "Hello"
    assert msgs[1].text == "Echo: Hello"

