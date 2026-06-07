import pytest
from flexagent.orchestration import AgentProfile, Event, InMemoryMessageBus, GroupChat

def test_agent_profile():
    profile = AgentProfile(
        name="TestAgent",
        description="A test agent",
        instructions="Do testing"
    )
    assert profile.name == "TestAgent"
    assert profile.description == "A test agent"
    assert profile.instructions == "Do testing"
    assert profile.tools == []

def test_message_bus():
    bus = InMemoryMessageBus()
    received_events = []
    
    def callback(event: Event):
        received_events.append(event)
        
    bus.subscribe("test_topic", callback)
    
    event = Event(topic="test_topic", sender="sender_1", payload={"msg": "hello"})
    bus.publish(event)
    
    assert len(received_events) == 1
    assert received_events[0].topic == "test_topic"
    assert received_events[0].payload["msg"] == "hello"

def test_group_chat():
    bus = InMemoryMessageBus()
    chat = GroupChat(message_bus=bus)
    
    agent1 = AgentProfile(name="Agent1", description="desc1", instructions="inst1")
    agent2 = AgentProfile(name="Agent2", description="desc2", instructions="inst2")
    
    chat.add_agent(agent1)
    chat.add_agent(agent2)
    
    agent1_messages = []
    agent2_messages = []
    
    bus.subscribe("agent_Agent1", lambda e: agent1_messages.append(e))
    bus.subscribe("agent_Agent2", lambda e: agent2_messages.append(e))
    
    # Broadcast message from Agent1
    chat.send_message(sender="Agent1", content="hello everyone")
    
    assert len(agent1_messages) == 0  # Should not receive its own broadcast
    assert len(agent2_messages) == 1
    assert agent2_messages[0].payload["content"] == "hello everyone"
    
    # Directed message to Agent1 from Agent2
    chat.send_message(sender="Agent2", content="hello Agent1", target="Agent1")
    
    assert len(agent1_messages) == 1
    assert len(agent2_messages) == 1  # unchanged
    assert agent1_messages[0].payload["content"] == "hello Agent1"
