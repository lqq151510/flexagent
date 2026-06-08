from flexagent.core.agent_memory import AgentMemory, SystemMessage

class MessageCompactor:
    def __init__(self, memory: AgentMemory, max_messages: int = 10):
        self.memory = memory
        self.max_messages = max_messages

    def compact(self, session_id: str) -> None:
        messages = self.memory.get_messages(session_id)
        if len(messages) <= self.max_messages:
            return

        has_system = False
        if messages and isinstance(messages[0], SystemMessage):
            has_system = True

        to_keep = self.max_messages
        if has_system:
            to_keep -= 1
            
        recent = messages[-to_keep:] if to_keep > 0 else []
        
        new_messages = []
        if has_system:
            new_messages.append(messages[0])
        new_messages.extend(recent)
        
        self.memory.clear(session_id)
        self.memory.add_messages(session_id, new_messages)

