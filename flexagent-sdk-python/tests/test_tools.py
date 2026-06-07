import pytest
from flexagent.core.tools import tool, ToolExecutor

def test_tool_decorator():
    @tool(description="Adds two numbers")
    def add(a: int, b: int) -> int:
        return a + b
        
    assert add.name == "add"
    assert add.description == "Adds two numbers"
    
    # Test execution
    assert add.execute(a=1, b=2) == 3
    
    # Check schema
    schema = add.schema.model_json_schema()
    assert "a" in schema["properties"]
    assert schema["properties"]["a"]["type"] == "integer"
    assert "b" in schema["properties"]
    assert schema["properties"]["b"]["type"] == "integer"

def test_tool_executor():
    @tool()
    def multiply(x: int, y: int) -> int:
        """Multiplies two numbers"""
        return x * y
        
    @tool()
    def divide(x: float, y: float) -> float:
        if y == 0:
            raise ValueError("Division by zero")
        return x / y

    executor = ToolExecutor([multiply, divide])
    
    # Successful execution
    res = executor.execute("multiply", {"x": 3, "y": 4})
    assert res == 12
    
    # Error handling inside tool
    res = executor.execute("divide", {"x": 10.0, "y": 0.0})
    assert "Error validating tool divide: Division by zero" in res or "Error executing tool divide" in res
    
    # Error handling for validation
    res = executor.execute("multiply", {"x": "not a number", "y": 4})
    assert "Error validating tool multiply" in res
    
    # Tool not found
    res = executor.execute("subtract", {"a": 1})
    assert "Tool subtract not found" in res
