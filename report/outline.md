## What to include in the report?

### System's Perspective

A description and illustration of the:

  - Design and architecture of your _ITU-MiniTwit_ systems
  - All dependencies of your _ITU-MiniTwit_ systems on all levels of abstraction and development stages. That is, list and briefly describe all technologies and tools you applied and depend on.
  - Important interactions of subsystems.
    - For example, via an illustrative UML Sequence diagram that shows the flow of information through your system from user request in the browser, over all subsystems, hitting the database, and a response that is returned to the user.
    - Similarly, another illustrative sequence diagram that shows how requests from the simulator traverse your system.
  - Describe the current state of your systems, for example using results of static analysis and quality assessments.

MSc students should argue for the choice of technologies and decisions for at least all cases for which we asked you to do so in the tasks at the end of each session.


### Process' perspective

This perspective should clarify how code or other artifacts come from idea into the running system and everything that happens on the way.

In particular, the following descriptions should be included:

  - A complete description of stages and tools included in the CI/CD chains, including deployment and release of your systems.
  - How do you monitor your systems and what precisely do you monitor?
  - What do you log in your systems and how do you aggregate logs?
  - Brief results of the security assessment and brief description of how did you harden the security of your system based on the analysis.
  - Applied strategy for scaling and upgrades.

In case you have used AI-assistants during your project briefly explain which system(s) you used during the project and reflect how it supported or hindered your process.



### Reflection Perspective

Describe the biggest issues, how you solved them, and which are major lessons learned with regards to:

  - evolution and refactoring
  - operation, and
  - maintenance

of your _ITU-MiniTwit_ systems. Link back to respective commit messages, issues, tickets, etc. to illustrate these.


Also reflect and describe what was the "DevOps" style of your work.
For example, what did you do differently to previous development projects and how did it work?
