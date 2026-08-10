-- Phase 5 (商用): 会话级世界书绑定（由业务后端作为事实源）

ALTER TABLE app_conversation_st_binding
    ADD COLUMN st_world_names_json TEXT NULL;

