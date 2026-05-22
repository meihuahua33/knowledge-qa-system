# knowledge-qa-system
企业知识库智能问答系统
	技术栈：Spring Boot 3 + Spring AI Alibaba + DeepSeek API + Milvus + Redis + MySQL
	基于 Spring AI Alibaba 框架搭建 RAG 全链路知识库问答系统，支持 PDF/Word 文档解析 → Embedding 向量化 → Milvus 存储 → 语义检索 → DeepSeek LLM 生成的完整流程
	设计多源异构文档（PDF/Word/TXT）的知识采集与标准化处理流程，支持企业内部知识的统一入库管理
	实现 Elasticsearch 关键词检索 + Milvus 向量检索混合召回策略，结合 RRF 算法融合排序，有效提升复杂问题的检索准确性与召回覆盖率
	构建文档分类与标签体系，实现知识的结构化组织与跨部门复用，支持多维度条件过滤检索
	设计知识库版本管理机制，支持文档更新时的增量向量化，避免全量重建
	基于 SSE 实现 LLM 流式输出，使用 Redis 管理多轮对话上下文，结合 RabbitMQ 异步处理文档 Embedding 任务，避免大文件上传阻塞
	设计结构化 Prompt 模板，引入 Few-shot 示例与 JSON 输出约束，通过对比实验优化 Prompt，幻觉率降低明显，提升回答准确性与一致性
