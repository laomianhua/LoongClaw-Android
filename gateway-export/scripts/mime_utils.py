"""
共享 MIME 类型推断
供 file_manager.py、save_to_storage.py、upload_server.py 共用
"""


def guess_mime(file_name: str) -> str:
    """根据文件扩展名推断 MIME 类型"""
    ext = file_name.rsplit(".", 1)[-1].lower() if "." in file_name else ""
    return {
        # 图片
        "jpg": "image/jpeg",
        "jpeg": "image/jpeg",
        "png": "image/png",
        "webp": "image/webp",
        "gif": "image/gif",
        "bmp": "image/bmp",
        # 文档
        "pdf": "application/pdf",
        "doc": "application/msword",
        "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "xls": "application/vnd.ms-excel",
        "xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "ppt": "application/vnd.ms-powerpoint",
        "pptx": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        # 网页 / 文本
        "html": "text/html",
        "htm": "text/html",
        "txt": "text/plain",
        "md": "text/markdown",
        "csv": "text/csv",
        "json": "application/json",
    }.get(ext, "application/octet-stream")
