import uuid
from typing import Callable, Dict, List, Any, Optional
from pydantic import BaseModel, Field

class Event(BaseModel):
    id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    topic: str
    payload: Dict[str, Any]
    sender: str
    
class InMemoryMessageBus:
    def __init__(self):
        self.subscribers: Dict[str, List[Callable[[Event], None]]] = {}
        
    def subscribe(self, topic: str, callback: Callable[[Event], None]):
        if topic not in self.subscribers:
            self.subscribers[topic] = []
        self.subscribers[topic].append(callback)
        
    def publish(self, event: Event):
        if event.topic in self.subscribers:
            for callback in self.subscribers[event.topic]:
                callback(event)
                
    def clear(self):
        self.subscribers.clear()
