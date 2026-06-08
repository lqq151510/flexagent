from flexagent.core.agent_memory import InMemoryAgentMemory, SystemMessage, UserMessage, AssistantMessage
from flexagent.core.compaction import MessageCompactor

def test_compactor():
    memory = InMemoryAgentMemory()
    session_id = "test_session"
    compactor = MessageCompactor(memory=memory, max_messages=3)
    
    memory.add_message(session_id, SystemMessage(text="system"))
    memory.add_message(session_id, UserMessage(text="msg1"))
    memory.add_message(session_id, AssistantMessage(text="reply1"))
    memory.add_message(session_id, UserMessage(text="msg2"))
    memory.add_message(session_id, AssistantMessage(text="reply2"))
    
    assert len(memory.get_messages(session_id)) == 5
    compactor.compact(session_id)
    
    msgs = memory.get_messages(session_id)
    assert len(msgs) == 3
    assert msgs[0].text == "system"
    assert msgs[1].text == "msg2"
    assert msgs[2].text == "reply2"

def test_compactor_no_system():
    memory = InMemoryAgentMemory()
    session_id = "test_session_2"
    compactor = MessageCompactor(memory=memory, max_messages=2)
    
    memory.add_message(session_id, UserMessage(text="msg1"))
    memory.add_message(session_id, AssistantMessage(text="reply1"))
    memory.add_message(session_id, UserMessage(text="msg2"))
    
    compactor.compact(session_id)
    msgs = memory.get_messages(session_id)
    assert len(msgs) == 2
    assert msgs[0].text == "reply1"
    assert msgs[1].text == "msg2"
