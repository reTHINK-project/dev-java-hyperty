## eLearning Rating

**Address:** `hyperty://sharing-cities-dsm/elearning-rating`

Additional configuration data:

```
{
  tokens_per_answer: <integer>
  tokens_per_correct_answer: <integer>
}
```

The eLearning Rating Hyperty observes eLearning answers produced by user's eLearning Player Hyperty to reward with tokens the individual wallet in case the answers are correct.


![Elearning Server](elearning_server.png)

### Persisted Data Model

This Hyperty handles the storage of Elearning contents compliant with the [Elearning Data Object Model](https://github.com/reTHINK-project/specs/tree/master/datamodel/data-objects/elearning).

### Observed Streams

* Citizen Elearning answers:

- data://<domain>/<identifier>

### Produced Stream

* elearning content data: `data://sharing-cities-dsm/elearning`

