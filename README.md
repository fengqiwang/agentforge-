# AgentForge — Agentic Data Analysis Platform

Natural language to SQL, powered by a multi-agent pipeline. Ask business questions in plain English or Chinese, get charts and data back.

![](https://img.shields.io/badge/Java-21-orange) ![](https://img.shields.io/badge/Spring_Boot-3.5-green) ![](https://img.shields.io/badge/Vue-3.5-blue) ![](https://img.shields.io/badge/LangChain4j-1.9-red)

## Architecture

```
User: "What was the total transaction amount last month?"
  │
  ▼
┌─────────────────────────────────────────────────────────┐
│                    Agent Pipeline                        │
│                                                          │
│  Router ──→ SchemaAgent ──→ SqlGenerator ──→ Validator  │
│              (RAG search)    (3-level gen)    (AST check)│
│                                                          │
│  → Fixer (retry ×3) → Executor → Review → Formatter     │
└─────────────────────────────────────────────────────────┘
  │                                    │
  ▼                                    ▼
  MySQL          ChromaDB           Chart + Table
 (400+ tables)  (vector search)    (ECharts)
```

### SQL Generation — Three Levels

| Level | Strategy | Accuracy | Latency | Tokens |
|-------|----------|----------|---------|--------|
| L1 | Template matching (regex/keyword) | ~100% | <10ms | 0 |
| L2 | Few-Shot RAG (vector similarity) | ~80% | ~2s | ~1K |
| L3 | LLM free generation (DeepSeek) | ~50-60% | ~3s | ~2K |

### Module Map

```
agentforge-common       Shared models, exceptions, utilities
agentforge-framework    LLM clients, RAG engine, Redis, memory, metrics
agentforge-safety       SQL validation (JSqlParser AST), JWT, data masking
agentforge-report       SQL generation, reports, reconciliation, insights
agentforge-workflow     Multi-agent orchestration pipeline
agentforge-code         Automated code review agent
agentforge-web          Spring Boot application, REST API, Flyway migrations
agentforge-seeder       Test data generator
agentforge-ui           Vue 3 + Vite + Element Plus frontend
```

## Quick Start

### Prerequisites

- **Docker** (for containerized deployment)
- **API Keys**: [DeepSeek](https://platform.deepseek.com/api_keys) (LLM) + [Zhipu BigModel](https://open.bigmodel.cn/) (embeddings)
- **JDK 21** + **Maven 3.9** + **Node.js 22** (for local development)

### Option 1: Docker Compose (full stack, for new users)

```bash
# Clone
git clone https://github.com/YOUR_USERNAME/agentforge.git
cd agentforge

# Configure API keys
cp .env.example .env
# Edit .env → set LLM_API_KEY and EMBEDDING_API_KEY

# Start everything (MySQL + Redis + ChromaDB + Backend + Frontend)
docker compose -f docker-compose.thirdparty.yml up -d

# Optional: monitoring stack
docker compose -f docker-compose.thirdparty.yml --profile monitoring up -d

# Open http://localhost:5173
```

### Option 2: Docker Compose (app only, existing infrastructure)

If you already have MySQL, Redis, ChromaDB, and Nacos running:

```bash
docker compose up -d
# Backend on :8080, Frontend on :5173
```

The backend pulls all config from your existing Nacos server.

### Option 3: Local Development

```bash
# Backend
mvn -pl agentforge-web -am spring-boot:run

# Frontend (separate terminal)
cd agentforge-ui && npm install && npm run dev
# Opens http://localhost:5173, proxies /api to :8080
```

## Configuration

All runtime config is managed by **Nacos** (dev) or **environment variables** (Docker).

| Variable | Default | Description |
|----------|---------|-------------|
| `LLM_API_KEY` | — | DeepSeek API key (required) |
| `EMBEDDING_API_KEY` | — | Zhipu embedding API key (required) |
| `LLM_BASE_URL` | `https://api.deepseek.com/` | LLM API endpoint |
| `LLM_CHAT_MODEL` | `deepseek-v4-flash` | Model name |
| `EMBEDDING_MODEL` | `embedding-3` | Embedding model name |
| `MYSQL_HOST` | `mysql` | MySQL host |
| `REDIS_HOST` | `redis` | Redis host |
| `CHROMA_HOST` | `chroma` | ChromaDB host |

See `.env.example` for the full list.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.5, LangChain4j 1.9 |
| Frontend | Vue 3, Vite, Element Plus, ECharts |
| Database | MySQL 8.0 (Flyway migrations) |
| Cache | Redis 7 (Lettuce) |
| Vector DB | ChromaDB (HNSW, cosine similarity) |
| LLM | DeepSeek API (OpenAI-compatible) |
| Embeddings | Zhipu BigModel `embedding-3` (2048-dim) |
| SQL Safety | JSqlParser 5.3 (AST-level validation) |
| Resilience | Resilience4j (circuit breaker, retry, timeout) |
| Monitoring | Micrometer → Prometheus → Grafana |
| Config | Nacos (dev) / env vars (Docker) |

## API Endpoints

| Endpoint | Description |
|----------|-------------|
| `POST /api/chat/stream` | SSE streaming chat (main entry) |
| `POST /api/report/build` | Build report from natural language |
| `GET /api/report/list` | List saved reports |
| `GET /api/report/view/{token}` | View shared report |
| `POST /api/admin/schema/reindex` | Rebuild vector index |
| `POST /api/admin/schema/search` | Test schema RAG retrieval |
| `POST /api/reconciliation/upload` | Upload CSV for reconciliation |
| `GET /actuator/health` | Health check |
| `GET /actuator/prometheus` | Prometheus metrics |

## Project Structure

```
agentforge/
├── docker-compose.yml              # App only (existing infra)
├── docker-compose.thirdparty.yml   # Full stack (new users)
├── Dockerfile.backend              # Multi-stage Maven → JRE build
├── Dockerfile.frontend             # Multi-stage Node → Nginx build
├── nginx.conf                      # Nginx reverse proxy + SSE support
├── .env.example                    # Environment variable template
├── pom.xml                         # Parent POM
├── agentforge-common/              # Shared models & utilities
├── agentforge-framework/           # LLM, RAG, Redis, memory, metrics
├── agentforge-safety/              # SQL validation, JWT, data masking
├── agentforge-report/              # SQL generation, reports, prompts
├── agentforge-workflow/            # Agent pipeline orchestration
├── agentforge-code/                # Code review agent
├── agentforge-web/                 # Spring Boot app, controllers, migrations
├── agentforge-seeder/              # Test data generator
├── agentforge-ui/                  # Vue 3 frontend
├── docker/                         # Prometheus + Grafana configs
└── test/                           # JMeter load test plans
```

## License

Apache License 2.0 — see [LICENSE](LICENSE) for details.
