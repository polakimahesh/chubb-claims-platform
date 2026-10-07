# 02 - Architecture and implementation

Prompt used (verbatim):

> I'm thinking of using **Java Spring Boot with Gradle**. So, let's build the application using Gradle.
>
> Please follow all the steps and guidelines mentioned in the document. Make sure we follow the guidelines completely and implement **all the APIs that are expected or required**.
>
> If the guidelines allow us to use a **microservices architecture**, let's use microservices and build the application properly with a good architecture.
>
> If microservices are not required, we can build it as a single application and still implement the **Kafka producer and consumer** functionality within the application.
>
> But overall, let's build the project properly, following all the guidelines and best practices mentioned in the document. Okay?

Claude's decisions in response (two services split by read/write profile, outbox, etc.) are recorded in
docs/decisions-and-assumptions.md and ai-journal/claude-journal.md.
