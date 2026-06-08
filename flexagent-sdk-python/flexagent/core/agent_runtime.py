from abc import ABC, abstractmethod
from typing import Generic, TypeVar, Any, List
from flexagent.core.agent_memory import Message

T = TypeVar('T')

class AgentRuntime(ABC, Generic[T]):
    def __init__(self):
        self._history_messages: List[Message] = []
        self._session_id: str = None

    def set_history_messages(self, messages: List[Message]) -> None:
        self._history_messages = list(messages) if messages else []

    def get_history_messages(self) -> List[Message]:
        return list(self._history_messages)

    def set_session_id(self, session_id: str) -> None:
        self._session_id = session_id

    @abstractmethod
    def run(self, input_data: T) -> Any:
        pass

    @abstractmethod
    async def run_async(self, input_data: T) -> Any:
        pass

    def stream(self, input_data: T) -> Any:
        raise NotImplementedError("stream is not implemented")
        
    async def stream_async(self, input_data: T) -> Any:
        raise NotImplementedError("stream_async is not implemented")
