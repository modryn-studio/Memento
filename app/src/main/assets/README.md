# ONNX Model Assets

This directory should contain the following files for the embedding service:

1. **all-MiniLM-L6-v2.onnx** (~22MB)
   - Sentence transformer model for generating embeddings
   - Download from: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
   - Convert to ONNX format using the `optimum` library

2. **vocab.txt**
   - Tokenizer vocabulary file
   - Download from the same Hugging Face model repository

## How to obtain the model

### Option 1: Download pre-converted ONNX model
```bash
pip install optimum[onnxruntime]
optimum-cli export onnx --model sentence-transformers/all-MiniLM-L6-v2 ./onnx_model
```

### Option 2: Manual conversion
```python
from transformers import AutoTokenizer
from optimum.onnxruntime import ORTModelForFeatureExtraction

model = ORTModelForFeatureExtraction.from_pretrained(
    "sentence-transformers/all-MiniLM-L6-v2",
    export=True
)
model.save_pretrained("./onnx_model")

tokenizer = AutoTokenizer.from_pretrained("sentence-transformers/all-MiniLM-L6-v2")
tokenizer.save_pretrained("./onnx_model")
```

Then copy `model.onnx` as `all-MiniLM-L6-v2.onnx` and `vocab.txt` to this directory.

## Model Details

- **Dimensions:** 384
- **Max Sequence Length:** 256 tokens
- **Size:** ~22MB
- **Languages:** English (primarily)
- **Use case:** Semantic similarity, search, clustering
