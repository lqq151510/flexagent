import re
from typing import Any, AsyncGenerator, Callable, Dict, Generator, Optional
from flexagent.core.agent_runtime import AgentRuntime
from flexagent.core.agent_memory import UserMessage, AssistantMessage

class LLMRuntime(AgentRuntime[str]):
    def __init__(self, llm_callable: Callable, async_llm_callable: Optional[Callable] = None):
        super().__init__()
        self.llm_callable = llm_callable
        self.async_llm_callable = async_llm_callable

    def run(self, input_data: str) -> Dict[str, Any]:
        self._history_messages.append(UserMessage(text=input_data))
        response_text = self.llm_callable(self._history_messages)
        return self._process_response(response_text)

    async def run_async(self, input_data: str) -> Dict[str, Any]:
        self._history_messages.append(UserMessage(text=input_data))
        if self.async_llm_callable:
            response_text = await self.async_llm_callable(self._history_messages)
        else:
            response_text = self.llm_callable(self._history_messages)
        return self._process_response(response_text)

    def stream(self, input_data: str) -> Generator[Dict[str, Any], None, None]:
        self._history_messages.append(UserMessage(text=input_data))
        response_stream = self.llm_callable(self._history_messages, stream=True)
        full_response = ""
        
        for chunk in response_stream:
            full_response += chunk
            yield self._parse_chunk(chunk, full_response)
            
        self._history_messages.append(AssistantMessage(text=full_response))

    async def stream_async(self, input_data: str) -> AsyncGenerator[Dict[str, Any], None]:
        self._history_messages.append(UserMessage(text=input_data))
        response_stream = await self.async_llm_callable(self._history_messages, stream=True)
        full_response = ""
        
        async for chunk in response_stream:
            full_response += chunk
            yield self._parse_chunk(chunk, full_response)
            
        self._history_messages.append(AssistantMessage(text=full_response))

    def _process_response(self, response_text: str) -> Dict[str, Any]:
        think_blocks = re.findall(r'<think>(.*?)</think>', response_text, re.DOTALL)
        content_without_think = re.sub(r'<think>.*?</think>', '', response_text, flags=re.DOTALL).strip()
        self._history_messages.append(AssistantMessage(text=response_text))
        return {
            "content": content_without_think,
            "think": think_blocks,
            "raw": response_text
        }

    def _parse_chunk(self, chunk: str, full_response: str) -> Dict[str, Any]:
        think_blocks = re.findall(r'<think>(.*?)</think>', full_response, re.DOTALL)
        content_without_think = re.sub(r'<think>.*?</think>', '', full_response, flags=re.DOTALL).strip()
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
