import re
from typing import Any, AsyncGenerator, Callable, Dict, Generator, Optional
from flexagent.core.agent_runtime import AgentRuntime
from flexagent.core.agent_memory import AgentMemory, UserMessage, AssistantMessage

class LLMRuntime(AgentRuntime[str]):
    def __init__(self, memory: AgentMemory, llm_callable: Callable, async_llm_callable: Optional[Callable] = None):
        super().__init__(memory)
        self.llm_callable = llm_callable
        self.async_llm_callable = async_llm_callable

    def run(self, input_data: str) -> Dict[str, Any]:
        self.memory.add_message(UserMessage(content=input_data))
        messages = self.memory.get_messages()
        
        response_text = self.llm_callable(messages)
        return self._process_response(response_text)

    async def run_async(self, input_data: str) -> Dict[str, Any]:
        self.memory.add_message(UserMessage(content=input_data))
        messages = self.memory.get_messages()
        
        if self.async_llm_callable:
            response_text = await self.async_llm_callable(messages)
        else:
            response_text = self.llm_callable(messages)
            
        return self._process_response(response_text)

    def stream(self, input_data: str) -> Generator[Dict[str, Any], None, None]:
        self.memory.add_message(UserMessage(content=input_data))
        messages = self.memory.get_messages()
        
        response_stream = self.llm_callable(messages, stream=True)
        full_response = ""
        in_think = False
        think_content = ""
        
        for chunk in response_stream:
            full_response += chunk
            yield self._parse_chunk(chunk, full_response)
            
        self.memory.add_message(AssistantMessage(content=full_response))

    async def stream_async(self, input_data: str) -> AsyncGenerator[Dict[str, Any], None]:
        self.memory.add_message(UserMessage(content=input_data))
        messages = self.memory.get_messages()
        
        response_stream = await self.async_llm_callable(messages, stream=True)
        full_response = ""
        
        async for chunk in response_stream:
            full_response += chunk
            yield self._parse_chunk(chunk, full_response)
            
        self.memory.add_message(AssistantMessage(content=full_response))

    def _process_response(self, response_text: str) -> Dict[str, Any]:
        think_blocks = re.findall(r'<think>(.*?)</think>', response_text, re.DOTALL)
        content_without_think = re.sub(r'<think>.*?</think>', '', response_text, flags=re.DOTALL).strip()
        
        self.memory.add_message(AssistantMessage(content=response_text))
        
        return {
            "content": content_without_think,
            "think": think_blocks,
            "raw": response_text
        }

    def _parse_chunk(self, chunk: str, full_response: str) -> Dict[str, Any]:
        # Simple extraction for current state in a stream
        think_blocks = re.findall(r'<think>(.*?)</think>', full_response, re.DOTALL)
        content_without_think = re.sub(r'<think>.*?</think>', '', full_response, flags=re.DOTALL).strip()
        
        # Check if we are currently inside a think block
        in_think = False
        last_think_open = full_response.rfind('<think>')
        last_think_close = full_response.rfind('</think>')
        if last_think_open > last_think_close:
            in_think = True
            
        return {
            "chunk": chunk,
            "in_think": in_think,
            "content_so_far": content_without_think,
            "think_so_far": think_blocks
        }
