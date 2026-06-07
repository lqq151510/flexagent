import pytest
from flexagent.core.agent_memory import (
    InMemoryAgentMemory,
    SystemMessage,
    UserMessage,
    AssistantMessage
)
from flexagent.core.agent_runtime import AgentRuntime

def test_in_memory_agent_memory():
    memory = InMemoryAgentMemory()
    assert len(memory.get_messages()) == 0

    sys_msg = SystemMessage(content="You are a helpful assistant.")
    memory.add_message(sys_msg)
    assert len(memory.get_messages()) == 1
    assert memory.get_messages()[0].role == "system"

    user_msg = UserMessage(content="Hello")
    memory.add_message(user_msg)
    assert len(memory.get_messages()) == 2

    ast_msg = AssistantMessage(content="Hi there!")
    memory.add_message(ast_msg)
    assert len(memory.get_messages()) == 3

    memory.clear()
    assert len(memory.get_messages()) == 0

class DummyRuntime(AgentRuntime[str]):
    def run(self, input_data: str) -> str:
        self.memory.add_message(UserMessage(content=input_data))
        response = f"Echo: {input_data}"
        self.memory.add_message(AssistantMessage(content=response))
        return response

    async def run_async(self, input_data: str) -> str:
        return self.run(input_data)

def test_agent_runtime():
    memory = InMemoryAgentMemory()
    runtime = DummyRuntime(memory)
    
    result = runtime.run("Test input")
    assert result == "Echo: Test input"
    
    messages = memory.get_messages()
    assert len(messages) == 2
    assert messages[0].content == "Test input"
    assert messages[1].content == "Echo: Test input"

@pytest.mark.asyncio
async def test_agent_runtime_async():
    memory = InMemoryAgentMemory()
    runtime = DummyRuntime(memory)
    
    result = await runtime.run_async("Test async input")
    assert result == "Echo: Test async input"
    
    messages = memory.get_messages()
    assert len(messages) == 2
