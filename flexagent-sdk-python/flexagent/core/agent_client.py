import logging
from typing import List, Callable, Optional, Dict, Any, AsyncGenerator, Generator
from flexagent.core.agent_memory import AgentMemory, AgentMessage, UserMessage, AssistantMessage
from flexagent.core.agent_runtime import AgentRuntime

logger = logging.getLogger(__name__)

class AgentClient:
    """
    The core facade for FlexAgent in Python, decoupled from specific LLM frameworks.
    Mirrors FlexAgentClient in Java.
    """
    def __init__(self, active_runtime: AgentRuntime, strategy: Any = None, memory: Optional[AgentMemory] = None, tool_executor: Optional[Callable] = None, initial_system_messages: Optional[List[AgentMessage]] = None):
        self.active_runtime = active_runtime
        self.strategy = strategy # Simplified strategy, can be added later
        self.memory = memory
        self.tool_executor = tool_executor
        self.initial_system_messages = initial_system_messages or []

    def generate(self, prompt: str, session_id: Optional[str] = None) -> AgentMessage:
        has_memory = bool(self.memory and session_id)
        
        logger.info(f"Generating response for prompt length: {len(prompt)}")
        
        if has_memory:
            agent_history = self.memory.get_messages(session_id)
            if agent_history:
                self.active_runtime.set_history_messages(agent_history)
            else:
                self.active_runtime.set_history_messages(self.initial_system_messages)
            self.active_runtime.set_session_id(session_id)
        else:
            self.active_runtime.set_history_messages(self.initial_system_messages)
            if session_id:
                self.active_runtime.set_session_id(session_id)
                
        try:
            if self.strategy:
                result_message = self.strategy.execute(prompt, self.active_runtime, self.tool_executor)
            else:
                result = self.active_runtime.run(prompt)
                if isinstance(result, AgentMessage):
                    result_message = result
                elif isinstance(result, dict):
                    result_message = AssistantMessage(text=result.get("content", str(result)))
                else:
                    result_message = AssistantMessage(text=str(result))
            
            if has_memory:
                updated_messages = self.active_runtime.get_history_messages()
                if updated_messages:
                    self.memory.clear(session_id)
                    self.memory.add_messages(session_id, updated_messages)
                else:
                    self.memory.add_message(session_id, UserMessage(text=prompt))
                    self.memory.add_message(session_id, result_message)
                    
            return result_message
        except Exception as e:
            logger.error("Error executing FlexAgent Agent Runtime", exc_info=True)
            raise

    async def generate_async(self, prompt: str, session_id: Optional[str] = None) -> AgentMessage:
        has_memory = bool(self.memory and session_id)
        
        if has_memory:
            agent_history = self.memory.get_messages(session_id)
            if agent_history:
                self.active_runtime.set_history_messages(agent_history)
            else:
                self.active_runtime.set_history_messages(self.initial_system_messages)
            self.active_runtime.set_session_id(session_id)
        else:
            self.active_runtime.set_history_messages(self.initial_system_messages)
            if session_id:
                self.active_runtime.set_session_id(session_id)
                
        try:
            if hasattr(self.strategy, "execute_async") and self.strategy:
                result_message = await self.strategy.execute_async(prompt, self.active_runtime, self.tool_executor)
            else:
                result = await self.active_runtime.run_async(prompt)
                if isinstance(result, AgentMessage):
                    result_message = result
                elif isinstance(result, dict):
                    result_message = AssistantMessage(text=result.get("content", str(result)))
                else:
                    result_message = AssistantMessage(text=str(result))
            
            if has_memory:
                updated_messages = self.active_runtime.get_history_messages()
                if updated_messages:
                    self.memory.clear(session_id)
                    self.memory.add_messages(session_id, updated_messages)
                else:
                    self.memory.add_message(session_id, UserMessage(text=prompt))
                    self.memory.add_message(session_id, result_message)
                    
            return result_message
        except Exception as e:
            logger.error("Error executing FlexAgent Agent Runtime", exc_info=True)
            raise

    def stream(self, prompt: str, session_id: Optional[str] = None) -> Generator[str, None, None]:
        has_memory = bool(self.memory and session_id)
        
        if has_memory:
            agent_history = self.memory.get_messages(session_id)
            if agent_history:
                self.active_runtime.set_history_messages(agent_history)
            else:
                self.active_runtime.set_history_messages(self.initial_system_messages)
            self.active_runtime.set_session_id(session_id)
        else:
            self.active_runtime.set_history_messages(self.initial_system_messages)
            if session_id:
                self.active_runtime.set_session_id(session_id)
                
        full_response = ""
        try:
            if hasattr(self.active_runtime, "stream"):
                for chunk in self.active_runtime.stream(prompt):
                    token = chunk.get("chunk", "") if isinstance(chunk, dict) else str(chunk)
                    full_response += token
                    yield token
            else:
                raise NotImplementedError("stream not implemented on active_runtime")
            
            if has_memory:
                updated_messages = self.active_runtime.get_history_messages()
                if updated_messages:
                    self.memory.clear(session_id)
                    self.memory.add_messages(session_id, updated_messages)
                else:
                    self.memory.add_message(session_id, UserMessage(text=prompt))
                    self.memory.add_message(session_id, AssistantMessage(text=full_response))
                    
        except Exception as e:
            logger.error("Error executing FlexAgent stream", exc_info=True)
            raise

    async def stream_async(self, prompt: str, session_id: Optional[str] = None) -> AsyncGenerator[str, None]:
        has_memory = bool(self.memory and session_id)
        
        if has_memory:
            agent_history = self.memory.get_messages(session_id)
            if agent_history:
                self.active_runtime.set_history_messages(agent_history)
            else:
                self.active_runtime.set_history_messages(self.initial_system_messages)
            self.active_runtime.set_session_id(session_id)
        else:
            self.active_runtime.set_history_messages(self.initial_system_messages)
            if session_id:
                self.active_runtime.set_session_id(session_id)
                
        full_response = ""
        try:
            if hasattr(self.active_runtime, "stream_async"):
                async for chunk in self.active_runtime.stream_async(prompt):
                    token = chunk.get("chunk", "") if isinstance(chunk, dict) else str(chunk)
                    full_response += token
                    yield token
            else:
                raise NotImplementedError("stream_async not implemented on active_runtime")
            
            if has_memory:
                updated_messages = self.active_runtime.get_history_messages()
                if updated_messages:
                    self.memory.clear(session_id)
                    self.memory.add_messages(session_id, updated_messages)
                else:
                    self.memory.add_message(session_id, UserMessage(text=prompt))
                    self.memory.add_message(session_id, AssistantMessage(text=full_response))
                    
        except Exception as e:
            logger.error("Error executing FlexAgent stream_async", exc_info=True)
            raise

