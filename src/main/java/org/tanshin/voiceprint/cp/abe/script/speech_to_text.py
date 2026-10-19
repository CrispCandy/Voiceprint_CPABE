import os
# 1. 核心彻底禁用 tqdm 进度条（必须放在最上面，在导入 whisper 之前）
os.environ["TQDM_DISABLE"] = "1"

import whisper
import sys
import warnings
import contextlib
import io

warnings.filterwarnings("ignore")

# 强制 UTF-8 输出
sys.stdout.reconfigure(encoding='utf-8')

audio_path = sys.argv[1]

# 加载模型
model = whisper.load_model("small")

# 2. 同时屏蔽 stdout（标准输出）和 stderr（标准错误），防止任何底层杂音输出
with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
    result = model.transcribe(
        audio_path,
        language='zh',
        verbose=False,
        fp16=False,
        without_timestamps=True
    )

# 3. 此时 stdout 已经恢复，只输出干净的最终识别文本
# 使用 strip() 清除两端的空格或换行符
print(result["text"].strip())