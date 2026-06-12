from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    glm_api_key: str = ""
    glm_chat_url: str = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    glm_chat_model: str = "glm-4-flash"
    glm_timeout_seconds: int = 60

    mysql_host: str = "127.0.0.1"
    mysql_port: int = 3306
    mysql_user: str = "archive_app"
    mysql_password: str = ""
    mysql_database: str = "archive_db"

    qa_agent_host: str = "127.0.0.1"
    qa_agent_port: int = 8001
    agent_max_iterations: int = 5


settings = Settings()
