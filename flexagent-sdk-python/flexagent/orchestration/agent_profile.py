from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional

class AgentProfile(BaseModel):
    name: str = Field(..., description="Name of the agent")
    description: str = Field(..., description="Description of the agent's capabilities")
    instructions: str = Field(..., description="System prompt or instructions for the agent")
    tools: List[str] = Field(default_factory=list, description="List of tool names available to the agent")
    metadata: Dict[str, Any] = Field(default_factory=dict, description="Additional metadata")
