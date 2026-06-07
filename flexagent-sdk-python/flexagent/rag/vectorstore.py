import math
from typing import List, Dict, Tuple, Any, Optional

class InMemoryVectorStore:
    def __init__(self):
        # Store tuple of (id, vector, metadata)
        self.documents: List[Tuple[str, List[float], Dict[str, Any]]] = []
        
    def add(self, doc_id: str, vector: List[float], metadata: Optional[Dict[str, Any]] = None):
        if metadata is None:
            metadata = {}
        self.documents.append((doc_id, vector, metadata))
        
    @staticmethod
    def cosine_similarity(vec1: List[float], vec2: List[float]) -> float:
        if len(vec1) != len(vec2) or len(vec1) == 0:
            return 0.0
            
        dot_product = sum(a * b for a, b in zip(vec1, vec2))
        norm1 = math.sqrt(sum(a * a for a in vec1))
        norm2 = math.sqrt(sum(b * b for b in vec2))
        
        if norm1 == 0 or norm2 == 0:
            return 0.0
            
        return dot_product / (norm1 * norm2)
        
    def search(self, query_vector: List[float], top_k: int = 5) -> List[Tuple[str, float, Dict[str, Any]]]:
        results = []
        for doc_id, vector, metadata in self.documents:
            sim = self.cosine_similarity(query_vector, vector)
            results.append((doc_id, sim, metadata))
            
        # Sort by similarity descending
        results.sort(key=lambda x: x[1], reverse=True)
        return results[:top_k]
        
    def clear(self):
        self.documents.clear()
