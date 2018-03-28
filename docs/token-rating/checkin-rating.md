## Check-in Rating

**Address:** `hyperty://sharing-cities-dsm/checkin-rating`

Additional configuration data:

```
{
  tokens_per_checkin: <integer>,
  checkin_radius: <meters>,
  min_frequency: <hours>
}
```

The Check-in Rating Hyperty observes user's check-in location and reward with tokens the individual wallet in case it matches with some place.

![Check-in Server](checkin_server.png)

### Persisted Data Model

This Hyperty handles the storage of local shops information including its location, description and picture:

**shops**

```
id: "myid",
name: "Loja do Manel",
description: "A Loja do Manel é porreira",
picture: "https://xpto/manel.gif",
opening-hours: {
  monday: ['09:00-12:00', '13:00-18:00'],
  ...
  sunday: [],
  exceptions: [
    '2016-11-11': ['09:00-12:00'],
     '2016-12-25': [],
     '01-01': [], // Recurring on each 1st of january
     '12-25': ['09:00-12:00'], // Recurring on each 25th of december  ]
   }
location: { degrees-latitude: "", degrees-longitude: "" }
```



### Observed Streams

* Citizen Location:

- stores

### Produced Stream

* shops data: `data://sharing-cities-dsm/shops`
