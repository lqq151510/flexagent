from abc import ABC, abstractmethod
from typing import List, Literal, Union, Dict, Any, Optional
from pydantic import BaseModel, Field

class AgentMessage(BaseModel):
    role: str
    text: str = ""
    tool_calls: List[Dict[str, Any]] = Field(default_factory=list)
    tool_id: Optional[str] = None
    tool_name: Optional[str] = None
    
    def __init__(self, **data):
        if 'content' in data and 'text' not in data:
            data['text'] = data.pop('content')
        super().__init__(**data)

    @property
    def content(self) -> str:
        return self.text
        
    @content.setter
    def content(self, value: str):
        self.text = value

class SystemMessage(AgentMessage):
    role: Literal["system"] = "system"

class UserMessage(AgentMessage):
    role: Literal["user"] = "user"

class AssistantMessage(AgentMessage):
    role: Literal["assistant"] = "assistant"

class ToolMessage(AgentMessage):
    role: Literal["tool"] = "tool"

Message = Union[SystemMessage, UserMessage, AssistantMessage, ToolMessage]

class AgentMemory(ABC):
    @abstractmethod
    def add_message(self, session_id: str, message: Message) -> None:
        pass

    def add_messages(self, session_id: str, messages: List[Message]) -> None:
        if messages:
            for msg in messages:
                self.add_message(session_id, msg)

    @abstractmethod
    def get_messages(self, session_id: str) -> List[Message]:
        pass

    @abstractmethod
    def clear(self, session_id: str) -> None:
        pass

class InMemoryAgentMemory(AgentMemory):
    def __init__(self):
        self._sessions: Dict[str, List[Message]] = {}

    def add_message(self, session_id: str, message: Message) -> None:
        if session_id not in self._sessions:
            self._sessions[session_id] = []
        self._sessions[session_id].append(message)

    def get_messages(self, session_id: str) -> List[Message]:
        return list(self._sessions.get(session_id, []))

    def clear(self, session_id: str) -> None:
        if session_id in self._sessions:
            self._sessions[session_id].clear()

    # Backwards compatibility methods
    def set_messages(self, session_id: str, messages: List[Message]) -> None:
        self._sessions[session_id] = list(messages)

