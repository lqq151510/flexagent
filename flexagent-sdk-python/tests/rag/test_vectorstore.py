import pytest
from flexagent.rag import InMemoryVectorStore

def test_vectorstore():
    store = InMemoryVectorStore()
    
    # Store some vectors
    store.add("doc1", [1.0, 0.0, 0.0], {"title": "Doc 1"})
    store.add("doc2", [0.0, 1.0, 0.0], {"title": "Doc 2"})
    store.add("doc3", [0.707, 0.707, 0.0], {"title": "Doc 3"})
    
    # Search for [1.0, 0.0, 0.0]
    results = store.search([1.0, 0.0, 0.0], top_k=2)
    
    assert len(results) == 2
    assert results[0][0] == "doc1"
    assert results[0][1] == pytest.approx(1.0)
    assert results[1][0] == "doc3"
    assert results[1][1] == pytest.approx(0.707, rel=1e-3)
    
    # Search for [0.0, 1.0, 0.0]
    results2 = store.search([0.0, 1.0, 0.0], top_k=1)
    assert len(results2) == 1
    assert results2[0][0] == "doc2"
    assert results2[0][1] == pytest.approx(1.0)

def test_cosine_similarity_edge_cases():
    sim = InMemoryVectorStore.cosine_similarity([0.0, 0.0], [0.0, 0.0])
    assert sim == 0.0
    
    sim = InMemoryVectorStore.cosine_similarity([1.0], [1.0, 2.0])
    assert sim == 0.0
    
    sim = InMemoryVectorStore.cosine_similarity([], [])
    assert sim == 0.0
