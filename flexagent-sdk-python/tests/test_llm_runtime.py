import pytest
from flexagent.core.agent_memory import InMemoryAgentMemory
from flexagent.adapters.llm_runtime import LLMRuntime

def mock_llm(messages, stream=False):
    if stream:
        def generator():
            yield "<think>thinking process"
            yield "</think>\nFinal answer."
        return generator()
    else:
        return "<think>some thought</think>\nThe answer."

async def mock_async_llm(messages, stream=False):
    if stream:
        async def async_generator():
            yield "<think>async thought"
            yield "</think>\nAsync answer."
        return async_generator()
    else:
        return "<think>async thought</think>\nAsync answer."

def test_llm_runtime_run():
    memory = InMemoryAgentMemory()
    runtime = LLMRuntime(memory=memory, llm_callable=mock_llm)
    
    result = runtime.run("Hello")
    assert "content" in result
    assert result["content"] == "The answer."
    assert "some thought" in result["think"]
    
    messages = memory.get_messages()
    assert len(messages) == 2
    assert messages[-1].content == "<think>some thought</think>\nThe answer."

@pytest.mark.asyncio
async def test_llm_runtime_run_async():
    memory = InMemoryAgentMemory()
    runtime = LLMRuntime(memory=memory, llm_callable=mock_llm, async_llm_callable=mock_async_llm)
    
    result = await runtime.run_async("Hello")
    assert result["content"] == "Async answer."
    assert "async thought" in result["think"]

def test_llm_runtime_stream():
    memory = InMemoryAgentMemory()
    runtime = LLMRuntime(memory=memory, llm_callable=mock_llm)
    
    chunks = list(runtime.stream("Hello"))
    assert len(chunks) == 2
    assert chunks[-1]["content_so_far"] == "Final answer."
    assert chunks[-1]["think_so_far"][0] == "thinking process"

@pytest.mark.asyncio
async def test_llm_runtime_stream_async():
    memory = InMemoryAgentMemory()
    runtime = LLMRuntime(memory=memory, llm_callable=mock_llm, async_llm_callable=mock_async_llm)
    
    chunks = []
    async for chunk in runtime.stream_async("Hello"):
        chunks.append(chunk)
        
    assert len(chunks) == 2
    assert chunks[-1]["content_so_far"] == "Async answer."
