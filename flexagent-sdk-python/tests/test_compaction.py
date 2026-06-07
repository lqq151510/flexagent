import pytest
from flexagent.core.agent_memory import InMemoryAgentMemory, SystemMessage, UserMessage, AssistantMessage
from flexagent.core.compaction import SlidingWindow

def test_sliding_window_compaction():
    memory = InMemoryAgentMemory()
    compaction = SlidingWindow(max_messages=3)
    
    memory.add_message(SystemMessage(content="system"))
    memory.add_message(UserMessage(content="msg1"))
    memory.add_message(AssistantMessage(content="reply1"))
    memory.add_message(UserMessage(content="msg2"))
    memory.add_message(AssistantMessage(content="reply2"))
    
    assert len(memory.get_messages()) == 5
    
    compaction.compact(memory)
    messages = memory.get_messages()
    
    # Should keep 3 messages: system, and the last 2
    assert len(messages) == 3
    assert messages[0].content == "system"
    assert messages[1].content == "msg2"
    assert messages[2].content == "reply2"

def test_sliding_window_no_system_message():
    memory = InMemoryAgentMemory()
    compaction = SlidingWindow(max_messages=2)
    
    memory.add_message(UserMessage(content="msg1"))
    memory.add_message(AssistantMessage(content="reply1"))
    memory.add_message(UserMessage(content="msg2"))
    
    compaction.compact(memory)
    messages = memory.get_messages()
    
    assert len(messages) == 2
    assert messages[0].content == "reply1"
    assert messages[1].content == "msg2"
