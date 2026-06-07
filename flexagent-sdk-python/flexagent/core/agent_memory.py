from abc import ABC, abstractmethod
from typing import List, Literal, Union, Dict, Any
from pydantic import BaseModel, Field

class AgentMessage(BaseModel):
    role: str
    content: str

class SystemMessage(AgentMessage):
    role: Literal["system"] = "system"

class UserMessage(AgentMessage):
    role: Literal["user"] = "user"

class AssistantMessage(AgentMessage):
    role: Literal["assistant"] = "assistant"
    tool_calls: List[Dict[str, Any]] = Field(default_factory=list)

Message = Union[SystemMessage, UserMessage, AssistantMessage]

class AgentMemory(ABC):
    @abstractmethod
    def add_message(self, message: Message) -> None:
        pass

    @abstractmethod
    def get_messages(self) -> List[Message]:
        pass

    @abstractmethod
    def clear(self) -> None:
        pass

    @abstractmethod
    def set_messages(self, messages: List[Message]) -> None:
        pass

class InMemoryAgentMemory(AgentMemory):
    def __init__(self):
        self._messages: List[Message] = []

    def add_message(self, message: Message) -> None:
        self._messages.append(message)

    def get_messages(self) -> List[Message]:
        return list(self._messages)

    def clear(self) -> None:
        self._messages.clear()

    def set_messages(self, messages: List[Message]) -> None:
        self._messages = list(messages)
