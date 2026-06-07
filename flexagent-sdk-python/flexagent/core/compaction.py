from abc import ABC, abstractmethod
from typing import List
from flexagent.core.agent_memory import AgentMemory, SystemMessage

class MemoryCompaction(ABC):
    @abstractmethod
    def compact(self, memory: AgentMemory) -> None:
        pass

class SlidingWindow(MemoryCompaction):
    def __init__(self, max_messages: int = 10):
        self.max_messages = max_messages
        
    def compact(self, memory: AgentMemory) -> None:
        messages = memory.get_messages()
        if len(messages) <= self.max_messages:
            return
            
        system_msgs = []
        other_msgs = messages
        
        if messages and isinstance(messages[0], SystemMessage):
            system_msgs.append(messages[0])
            other_msgs = messages[1:]
            
        keep_count = self.max_messages - len(system_msgs)
        if keep_count < 0:
            keep_count = 0
            
        new_messages = system_msgs + other_msgs[-keep_count:] if keep_count > 0 else system_msgs
        memory.set_messages(new_messages)
