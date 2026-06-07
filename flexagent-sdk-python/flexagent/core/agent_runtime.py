from abc import ABC, abstractmethod
from typing import Generic, TypeVar, Any
from flexagent.core.agent_memory import AgentMemory

T = TypeVar('T')

class AgentRuntime(ABC, Generic[T]):
    def __init__(self, memory: AgentMemory):
        self.memory = memory

    @abstractmethod
    def run(self, input_data: T) -> Any:
        pass

    @abstractmethod
    async def run_async(self, input_data: T) -> Any:
        pass
