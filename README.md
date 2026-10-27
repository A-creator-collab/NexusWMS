# NexusWMS - Autonomous Warehouse Management System

NexusWMS is a next-generation, AI-integrated desktop application designed to modernize warehouse operations. Moving beyond traditional CRUD systems, it features a modern "Enterprise SaaS" user interface, offline voice command capabilities, and autonomous AI agents that propose procurement strategies for human approval.

Built entirely in **Java** (JavaFX + Spring Boot + MySQL), it runs as a single, powerful desktop process.

## ✨ Key Features (The USP)

*   **Modern UI/UX:** A sleek, responsive JavaFX interface built with **AtlantaFX**, featuring light/dark mode toggles, polished data tables, and a professional dashboard.
*   **Autonomous AI Agents:** Leveraging **Spring AI (Anthropic/OpenAI)**, specialized agents (Replenishment, Risk, Order) analyze live database states, run mathematical forecasts, and generate `ApprovalRequests` for pending purchase orders.
*   **Human-in-the-Loop (HITL) Governance:** AI agents never execute financial actions blindly. They propose; a human manager must review and approve via the UI.
*   **Offline Voice Commands:** Integrated **Vosk** Speech-to-Text allows warehouse staff to execute hands-free commands (e.g., *"show low stock for packaging"*).
*   **Intelligent Forecasting:** Built-in algorithms for Exponential Smoothing, Safety Stock calculation, and dynamic Supplier Reliability Scoring.
*   **Robust Architecture:** Built with Spring Data JPA, strict transaction management, bean validation, and row-level locking to prevent concurrent inventory errors.

## 🛠️ Tech Stack

*   **Language:** Java 21 (Compatible with Java 26)
*   **Framework:** Spring Boot 3.4.1
*   **UI:** JavaFX 21 + AtlantaFX (Modern CSS styling) + Ikonli (Icons)
*   **Database:** MySQL 8.0
*   **AI Integration:** Spring AI 1.0.0 
*   **Voice Engine:** Vosk (Offline STT)
*   **Build Tool:** Apache Maven

---

## 🚀 Getting Started

### Prerequisites
*   **JDK 21** or higher
*   **Maven 3.8+**
*   **MySQL Server 8.0+**

### 1. Database Setup
Open your MySQL client (Workbench, Command Line, or XAMPP) and run:

```sql
CREATE DATABASE nexuswms CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'nexus'@'localhost' IDENTIFIED BY 'nexus';
GRANT ALL PRIVILEGES ON nexuswms.* TO 'nexus'@'localhost';
FLUSH PRIVILEGES;
```
Next, load the schema and seed data (navigate to src/main/resources/database/):
```
USE nexuswms;
source /path/to/NexusWMS/src/main/resources/database/schema.sql;
source /path/to/NexusWMS/src/main/resources/database/seed.sql;
```
2. Application Configuration

Open src/main/resources/application.properties and ensure your database credentials match:
```
spring.datasource.url=jdbc:mysql://localhost:3306/nexuswms?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
spring.datasource.username=nexus
spring.datasource.password=nexus
```
Note: If you have Windows Environment Variables set for NEXUS_DB_*, they will override this file. Delete them if you encounter connection issues).
3. Build & Run

Due to strict environment configurations, it is recommended to skip the AI-related unit tests during the initial build:
bash

mvn clean install -U "-Dmaven.test.skip=true"

CRITICAL: Do NOT run NexusWmsApplication.java. This project requires a custom launcher to ensure Spring Boot and JavaFX initialize in the correct order.

Run the application via the terminal:
bash

mvn javafx:run

4. Default Credentials

Upon first launch, you will be prompted to change the default password. Log in with:

    Username: admin

    Password: admin123

🧠 Architecture & Engineering Logic
System Architecture

 JavaFX UI (ui/*)  ── FxTaskHelper: Task on worker pool, Platform.runLater for UI ──┐
      │ app.bean(...)                                                               │
 Application services (service/*, forecasting/ForecastService, voice/*)            │
      │                              ▲ read-only tools                              │
 Agent layer (agents/*): Agent.propose() ─► AgentOrchestrator ─► approval_requests  │
      │                                        ApprovalService.approve() ─► services
 Persistence: Spring Data JPA / Hibernate (ddl-auto=validate) ─► MySQL 8 
 AssistantService: Spring AI ChatClient + WarehouseTools  ──or──  rule-based fallback 

Business Logic Highlights

    Supplier Reliability: Calculated as (onTime + 4) / (completed + 5). "On time" is defined as fully received on or before expected_date. New suppliers start with a baseline of 0.80.

    Safety Stock: ceil(z · σ_daily · √leadTime). The system uses a 95% service level (z ≈ 1.645).

    Forecasting: Utilizes daily demand over 90 days with Exponential Smoothing (α = 0.3) to predict reorder_qty.

    Concurrency Safety: Orders and approvals are row-locked (SELECT FOR UPDATE) to prevent double-clicks or multiple users from double-receiving stock.

    Offline Resiliency: If the AI API key is missing or fails, the system automatically degrades to deterministic, rule-based logic without crashing.

🐛 Troubleshooting

    Database Offline: The UI will show a red pill. Verify MySQL is running. Check that the nexuswms database exists and the nexus user has permissions.

    NoSuchBeanDefinitionException: ChatClient: Spring AI 1.0 requires specific modules. Ensure spring-ai-client-chat is in your pom.xml.

    Java 26 / ByteBuddy Errors: If you are on JDK 26, Mockito may fail to instrument classes. We bypassed this by skipping tests during the build.

    Voice Commands not working: You must download a Vosk model (e.g., vosk-model-small-en-us-0.15) and set the NEXUS_VOSK_MODEL_PATH environment variable to the unpacked folder.
