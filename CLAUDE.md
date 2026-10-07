# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Current state

The directory currently holds no source code, build files or git history. It contains only:

- `V2_BackEnd_candidate_assessment_brief.docx` — the assessment brief (summarised below)
- `How to Create Take home repository_Video.mp4` — a walkthrough video on setting up the take-home repository

Update this file with real build, lint and test commands and the actual architecture once code exists. Do not invent them before then.

## The assignment (Chubb APAC Backend Developer take-home)

Build the backend for a motor and property insurance claims platform covering six APAC markets. The frontend is out of scope. The time target is 2–3 hours, with a hard cap of 5.

- **Claimants** report an incident, track the claim, supply additional information when asked, and receive decisions.
- **Claims staff** pick up incoming claims, review and assess them, move them to settlement or rejection, and see team workload and performance.
- **Managers** need a view of outstanding claims and liability exposure.

### Constraints and requirements

- Stack: **Java/Spring Boot** (chosen; the brief also allowed C#/.NET). Database, caching, test libraries and API tooling are open.
- Communication: use both **REST/HTTP and Kafka**, and be able to justify which concern uses which. Likely split: synchronous request/response flows over REST, and asynchronous domain events over Kafka.
- The brief is deliberately underspecified. Service boundaries, data model and prioritisation are part of what is assessed. The brief asks these questions:
  - What are the core entities and how do they relate?
  - One service or several, and what drives that decision?
  - Which operations are synchronous and which are event-driven?
  - What is the read/write profile of each concern?
  - What must a claims officer retrieve efficiently to manage their workload?
  - How are claim lifecycle state transitions and their business rules handled?
  - How is outstanding liability exposure tracked?
- Prefer building a few things well over covering everything.

### Required deliverables (shape how work is done in this repo)

- A git repository with a **meaningful commit history** that shows the development process. Commit in small, logical steps.
- An app that **starts locally**. Provide a documented, reproducible way to run it, such as docker-compose for the database and Kafka.
- An **AI working journal**, committed alongside the code. It logs what was asked of the AI, what was accepted, challenged and overridden, and why. Keep it updated as work proceeds. It need not be polished.
- Supporting documentation, such as architecture and decision notes. Include the shortcuts taken and what would come next, because the walkthrough covers both.
- Every line submitted must be defensible in a 30–60 minute panel walkthrough ("why not X?"). Favour simple, explainable decisions over clever ones.
