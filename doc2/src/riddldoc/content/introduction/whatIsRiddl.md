---
title: "What is RIDDL?"
date: 2022-02-24T14:15:51-07:00
draft: true
weight: 10
---

RIDDL is a specification language for (potentially large) distributed systems borrowing concepts from [Domain Driven Design (DDD)](https://en.wikipedia.org/wiki/Domain-driven_design), [Reactive System Architecture](https://www.reactivemanifesto.org/), [agile user stories](https://en.wikipedia.org/wiki/User_story), [Behavior Driven Development (BDD)](https://en.wikipedia.org/wiki/Behavior-driven_development), and other widely adopted software development and design practices. It aims to capture business concepts and architectural details in a way that is consumable by business oriented professionals yet can be directly translated into varied technical and non-technical artifacts, including: 
* a documentation web site 
* various architectural diagrams (context maps, sequence diagrams, and so on) 
* code scaffolding that implments the design captured in the RIDDL specification 
* and many more

Using these outputs, delivery teams are well equipped to quickly get along with the task of implementation and  subsequent iterations on that design.

For the innately curious, RIDDL is an acronym for "Reactive Interface to Domain Definition Language". Whether we retain the acronym or add an e on the end to be more english friendly is yet to be determined.

## Based on DDD
RIDDL is based on concepts from [DDD](https://en.wikipedia.org/wiki/Domain-driven_design). This allows domain experts and technical teams to work at a higher level of abstraction using a ubiquitous language to develop a system specification that is familiar and comprehensible by business and technical leaders alike.

For best comprehension of the RIDDL language, it is best to be familiar with DDD concepts. For a four-minute overview [watch this video](https://elearn.domainlanguage.com/). For a more in depth understanding we recommend reading Vaughn Vernon's more concise book **[Domain Driven Design Distilled](https://www.amazon.com/Domain-Driven-Design-Distilled-Vaughn-Vernon/dp/0134434420/)** or Eric Evans' original tome **[Domain Driven Design: Tackling Complexity in the Heart of Software](https://www.amazon.com/Domain-Driven-Design-Tackling-Complexity-Software/dp/0321125215/)**.

## Reactive Architecture
The Reactive Manifesto was authored in 2014 by Jonas Bonér, David Farley, Roland Kunh, and Martin Thompson. As the computing landscape evolved and companies began to operate at "internet scale" it became evident that the old ways of constructing systems were not adequate. We needed an approach to system architecture that was fundamentally different in order to meet user expectations.

The fundamental objective in any system must be responsiveness. Responsive in the face of failure (resiliant) and responsive in the face of traffic demands (elastic). Users are conditioned to expect systems that perform well and are generally available. If these conditions are not met, users tend to go elsewhere to get what they want. 

Without going into too much detail here, the primary means of acheiving responsiveness, resiliance, and elasticity is by decomposing the concerns of a domain into well isolated blocks of code (DDD), and then, establishing clear non-blocking, asynchronous, message-driven interfaces between them.

To get more information on Reactive Architecture please refer to the excellent 6 part course by Lightbend. You can find the first course in that series [here](https://academy.lightbend.com/courses/course-v1:lightbend+LRA-IntroToReactive+v1/about).