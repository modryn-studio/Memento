# ONNX Model Assets

This directory should contain **compressed** model files to reduce APK size:

1. **all-MiniLM-L6-v2.onnx.gz** (~8MB compressed, ~22MB uncompressed)
   - Sentence transformer model for generating embeddings
   - Download uncompressed version from: https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
   - Convert to ONNX format using the `optimum` library
   - **Compress with gzip before adding to assets**

2. **vocab.txt.gz** (~200KB compressed)
   - Tokenizer vocabulary file
   - Download from the same Hugging Face model repository
   - **Compress with gzip before adding to assets**

## Model Decompression

The app automatically decompresses these files on first launch:
- `ModelSetupService` handles decompression
- Files are extracted to `context.filesDir/models/`
- Progress is shown during onboarding
- Only happens once per installation

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

Then compress and copy to this directory:
```bash
gzip -c model.onnx > all-MiniLM-L6-v2.onnx.gz
gzip -c vocab.txt > vocab.txt.gz
```

## Model Details

- **Dimensions:** 384
- **Max Sequence Length:** 256 tokens
- **Size:** ~22MB
- **Languages:** English (primarily)
- **Use case:** Semantic similarity, search, clustering
