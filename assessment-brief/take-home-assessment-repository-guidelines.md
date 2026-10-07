# Take-Home Assessment — Repository Guidelines

## Overview

The repository structure is one of the first things an examiner judges. Most examiners may spend less than ten minutes reviewing a submission initially, so the repository should be easy to understand, run, test, and trust.

A clean structure signals discipline and real engineering habits. A messy structure can raise doubts before the examiner even reads the code.

## AI-Assisted Development

This assignment is intended to be built using **Claude or a similar Large Language Model (LLM)**.

- Use Claude or a similar LLM during development.
- Keep **every prompt** used during development.
- Include the prompt files in the final repository.
- Maintain a **Claude AI journal** documenting important decisions and iterations.
- Time-box the assignment to approximately **3–5 hours**.
- Use prompt engineering effectively.
- Iterate, refine, retry, and regenerate as needed.
- Submit only when the repository satisfies all prerequisites and is genuinely ready.

## Recommended Spring Boot Repository Structure

```text
project-root/
├── README.md
├── docker-compose.yml
├── .gitignore
├── docs/
│   ├── architecture.md
│   ├── decisions-and-assumptions.md
│   └── walkthrough.md
├── prompts/
│   ├── 01-initial-analysis.md
│   ├── 02-architecture.md
│   ├── 03-implementation.md
│   ├── 04-testing.md
│   └── 05-final-review.md
├── ai-journal/
│   └── claude-journal.md
├── src/
│   ├── main/
│   │   ├── java/com/example/project/
│   │   │   ├── controller/
│   │   │   ├── service/
│   │   │   ├── repository/
│   │   │   ├── entity/
│   │   │   ├── dto/
│   │   │   ├── exception/
│   │   │   └── config/
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── db/migration/
│   │       └── openapi/
│   └── test/java/com/example/project/
│       ├── controller/
│       ├── service/
│       └── repository/
└── pom.xml
```

Adapt the exact folders to the actual take-home requirements.

## 1. README

`README.md` is one of the most important files.

It should explain:

- What the service does
- Features
- Architecture
- Technology stack
- Project structure
- Prerequisites
- How to run locally
- How to run tests
- API documentation
- Database setup
- Docker setup
- Important assumptions
- Design decisions
- Known limitations

Recommended sections:

```text
# Project Name
## Overview
## Features
## Technology Stack
## Architecture
## Project Structure
## Prerequisites
## Running Locally
## Running with Docker
## Running Tests
## API Documentation
## Database
## Configuration
## Assumptions
## Design Decisions
## Known Limitations
## AI-Assisted Development
## Future Improvements
```

The examiner should not need to guess how to start the application.

## 2. Layered Source Structure

Separate responsibilities clearly:

- `controller/` — HTTP endpoints, validation, request/response mapping
- `service/` — business rules and workflows
- `repository/` — database access
- `dto/` — API request/response models
- `entity/` — persistence models
- `exception/` — application exceptions and global handling
- `config/` — application/security/configuration classes

Controllers should not contain large amounts of business logic.

## 3. Tests

A visible, passing test suite is one of the strongest signals in a take-home submission.

Include appropriate:

- Unit tests
- Service tests
- Controller/API tests
- Repository/integration tests
- Validation and error-case tests

For Maven:

```bash
./mvnw test
```

or:

```bash
mvn test
```

Run the complete test suite before submission and verify that it passes.

## 4. Resources

Keep application resources organized:

```text
src/main/resources/
├── application.yml
├── db/
│   └── migration/
└── openapi/
```

This can contain configuration, database migrations, OpenAPI specifications, and other required resources.

## 5. API Contract

Include an API contract using OpenAPI/Swagger or Postman.

Document:

- Endpoints
- HTTP methods
- Request formats
- Response formats
- Validation rules
- Error responses
- Authentication requirements, if applicable

This shows that the API was designed intentionally before implementation.

## 6. One-Command Local Setup

The examiner should be able to start the project with minimal effort.

A common approach is:

```bash
docker compose up --build
```

Document the exact command in the README.

The goal is to avoid fifteen manual setup steps.

## 7. Decisions and Assumptions

Create:

```text
docs/decisions-and-assumptions.md
```

Document assumptions you had to make and important technical decisions.

Example:

```text
- User IDs are assumed to be unique.
- API timestamps are stored in UTC.
- Invalid requests return HTTP 400.
- PostgreSQL is used as the relational database.
- Flyway is used for database migrations.
- DTOs separate API contracts from persistence models.
```

The examiner should not have to guess why you made an important choice.

## 8. Prompts Folder

Create:

```text
prompts/
```

Keep the actual prompts used with Claude/LLM.

Example:

```text
prompts/
├── 01-initial-analysis.md
├── 02-architecture.md
├── 03-database-design.md
├── 04-api-implementation.md
├── 05-test-generation.md
├── 06-review-and-refactoring.md
└── 07-final-review.md
```

Do not create fake development history. Keep the prompts you actually used.

## 9. Claude AI Journal

Create:

```text
ai-journal/
└── claude-journal.md
```

Record the real development process:

```markdown
# Claude AI Development Journal

## Initial Analysis
Reviewed the requirements and identified the API, database,
validation, and testing requirements.

## Architecture
Discussed the layered Spring Boot architecture.

## Implementation
Used Claude to generate the initial implementation and reviewed
the generated code manually.

## Testing
Generated tests, reviewed them, and added missing edge cases.

## Iterations
Identified gaps and refined the implementation.

## Final Review
Reviewed structure, tests, documentation, configuration, and setup.
```

## 10. Architecture Diagram

A short diagram can communicate architecture better than several paragraphs.

```text
             Client
                |
                v
        +---------------+
        |   Controller  |
        +---------------+
                |
                v
        +---------------+
        |    Service    |
        +---------------+
                |
                v
        +---------------+
        |  Repository   |
        +---------------+
                |
                v
        +---------------+
        |   Database    |
        +---------------+
```

Keep the diagram consistent with the actual implementation.

## 11. Security and Secrets

Never commit:

- Passwords
- API keys
- Access tokens
- Private keys
- Database credentials
- Other sensitive secrets

Use environment variables or appropriate configuration.

Example:

```yaml
database:
  username: ${DB_USERNAME}
  password: ${DB_PASSWORD}
```

Use `.gitignore` to prevent accidental commits of build artifacts, IDE files, logs, and local secrets.

## 12. Git Practices

Avoid meaningless commit messages such as:

```text
update
fix
changes
final
final2
```

Prefer:

```text
Add user registration API
Implement user validation
Add database migration
Add service layer tests
Document local Docker setup
Fix duplicate user validation
```

Commit messages should describe the intent of the change.

## 13. Mistakes That Quietly Cost Points

Avoid:

- No README or a one-line README
- API, business logic, and database code tangled into one giant class
- Committed secrets or credentials
- Zero tests
- Tests that do not actually run
- Loose files dumped in the root
- Files named `Final`, `Final2`, `Copy`, etc.
- Poor or inconsistent naming

## 14. Examiner's Perspective

Before submitting, ask:

- Can I run this in under five minutes?
- Is the architecture obvious from the folder names?
- Did the candidate explain their decisions?
- Do the tests actually pass?
- Can I understand the API quickly?
- Can I understand the developer's reasoning?

Every file and folder should help answer one of these questions.

## 15. Five Things That Must Be Unmistakable

### Architecture
Use a short architecture diagram and clear package structure.

### Assumptions
Write down what you had to assume.

### Testing
Explain what is covered and exactly how to run the tests.

### Deployment / Local Setup
Document the one-command setup clearly.

### Documentation
Treat the README as part of the deliverable, not an afterthought.

## 16. Final Submission Checklist

- [ ] Root-level `README.md` exists
- [ ] README explains the project
- [ ] README explains architecture
- [ ] README explains how to run the project
- [ ] README explains how to run tests
- [ ] Source code has clear layers
- [ ] Controllers are separated from business logic
- [ ] Business logic is separated from database access
- [ ] Tests exist
- [ ] Tests pass
- [ ] Resources are organized
- [ ] Database migrations are included if required
- [ ] API contract is included
- [ ] Docker/local setup works
- [ ] Decisions and assumptions are documented
- [ ] Architecture diagram is included
- [ ] Prompts folder contains the actual prompts used
- [ ] Claude AI journal is included
- [ ] `.gitignore` is configured
- [ ] No credentials or secrets are committed
- [ ] Git commit messages are meaningful
- [ ] No unnecessary root files exist
- [ ] README has been reviewed from the examiner's perspective
- [ ] Complete test suite has been executed successfully
- [ ] Application has been tested from a clean setup
- [ ] Repository is ready for the final walkthrough

## 17. Suggested 3–5 Hour Time Box

```text
0:00 – 0:30   Understand requirements
0:30 – 1:00   Design architecture and API
1:00 – 2:30   Implement core functionality
2:30 – 3:15   Add tests and fix issues
3:15 – 3:45   Docker/setup verification
3:45 – 4:30   Documentation and AI journal
4:30 – 5:00   Final review and cleanup
```

Adjust this according to the actual assignment.

Use AI to accelerate implementation, but review and understand the generated code.

## 18. Final Principle

Treat the repository as your **first work sample**.

The examiner should be able to:

1. Understand what the project does.
2. Understand the architecture.
3. Run the project quickly.
4. Execute the tests.
5. Understand your technical decisions.
6. See evidence of AI-assisted development.
7. Review the implementation without unnecessary confusion.
8. Discuss the implementation with you during the final interview.

The goal is not simply to make the application work.

The goal is to make the **entire submission easy to evaluate and easy to trust**.

Build it, document it, test it, and know it well enough to explain every important decision confidently during the final video interview.
