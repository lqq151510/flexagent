import inspect
from typing import Any, Callable, Dict, List, Optional, Type
from pydantic import BaseModel, create_model

class Tool:
    def __init__(self, func: Callable, name: str, description: str, schema: Type[BaseModel]):
        self.func = func
        self.name = name
        self.description = description
        self.schema = schema

    def execute(self, **kwargs) -> Any:
        try:
            return self.func(**kwargs)
        except Exception as e:
            return f"Error executing tool {self.name}: {str(e)}"

def tool(name: Optional[str] = None, description: Optional[str] = None):
    def decorator(func: Callable) -> Tool:
        tool_name = name or func.__name__
        tool_desc = description or func.__doc__ or "No description provided."
        
        # Generate Pydantic schema from function signature
        sig = inspect.signature(func)
        fields = {}
        for param_name, param in sig.parameters.items():
            if param_name == 'self':
                continue
            annotation = param.annotation if param.annotation != inspect.Parameter.empty else Any
            default = param.default if param.default != inspect.Parameter.empty else ...
            fields[param_name] = (annotation, default)
            
        schema_model = create_model(f"{tool_name}Schema", **fields)
        
        return Tool(func=func, name=tool_name, description=tool_desc, schema=schema_model)
    return decorator

class ToolExecutor:
    def __init__(self, tools: List[Tool]):
        self.tools = {t.name: t for t in tools}
        
    def execute(self, tool_name: str, arguments: Dict[str, Any]) -> Any:
        if tool_name not in self.tools:
            return f"Error: Tool {tool_name} not found."
        
        t = self.tools[tool_name]
        try:
            # Validate with Pydantic
            validated_args = t.schema(**arguments)
            return t.execute(**validated_args.model_dump())
        except Exception as e:
            return f"Error validating tool {tool_name}: {str(e)}"
