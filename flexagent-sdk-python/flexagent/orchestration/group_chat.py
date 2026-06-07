from typing import List, Dict, Optional, Callable
from flexagent.orchestration.agent_profile import AgentProfile
from flexagent.orchestration.message_bus import InMemoryMessageBus, Event

class GroupChat:
    def __init__(self, message_bus: InMemoryMessageBus):
        self.agents: Dict[str, AgentProfile] = {}
        self.message_bus = message_bus
        self.history: List[Event] = []
        
        # Subscribe to group messages
        self.message_bus.subscribe("group_chat", self._handle_group_message)
        
    def add_agent(self, agent: AgentProfile):
        self.agents[agent.name] = agent
        
    def remove_agent(self, name: str):
        if name in self.agents:
            del self.agents[name]
            
    def get_agent(self, name: str) -> Optional[AgentProfile]:
        return self.agents.get(name)
        
    def send_message(self, sender: str, content: str, target: Optional[str] = None):
        """Send a message to the group or a specific target agent."""
        event = Event(
            topic="group_chat",
            sender=sender,
            payload={"content": content, "target": target}
        )
        self.message_bus.publish(event)
        
    def _handle_group_message(self, event: Event):
        self.history.append(event)
        # Routing logic: if target is specified, route to that agent
        # Otherwise, simple round-robin or broadcast could be applied.
        # For this minimal implementation, we just record it in history and 
        # publish a specific targeted event if needed.
        target = event.payload.get("target")
        if target and target in self.agents:
            # Route to specific agent
            self.message_bus.publish(Event(
                topic=f"agent_{target}",
                sender=event.sender,
                payload=event.payload
            ))
        elif not target:
            # Broadcast to all agents
            for agent_name in self.agents:
                if agent_name != event.sender:
                    self.message_bus.publish(Event(
                        topic=f"agent_{agent_name}",
                        sender=event.sender,
                        payload=event.payload
                    ))
