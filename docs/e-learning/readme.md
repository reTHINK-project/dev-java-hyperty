
## eLearning Hyperty

*ongoing work*

### option 1: e-learning stream per user

Each eLearning course includes:

- Elearning Content that is played by the Smart Citizen App using SAT exam model. When submitted the result is published with option selected per section.
- eLearning solution defines the right answers per section and the amount of credits per question.

The eLearning Server subscribes to Citizen eLearning stream and for each event received, the amount of credits is calculate and published in the Citizens eLearning Credits stream that is subscribed by the eLearning Rating Hyperty.

**Observed Streams**

* Citizen Learning

**Produced Streams**

* Citizen Learning Credits

### option 2: single e-learning stream

A single eLearning Parent Data Object is used where child data objects are published with eLearning submissions associated with user id.
