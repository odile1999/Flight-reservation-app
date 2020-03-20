# Flight Reservation Writeup





## Create New Tables

We created two new tables, Users, and Reservations table. 

**Users** table keeps track of users' information: username, hashed password, salt, and initial balance. The user uses create user command to insert tuple into the table, every username is unique and not null (primary key).

**Reservation** table keeps track of user's reservation information, each reservation has a unique reservation ID and is set to be automatic increment by one for every new reservation, when implementing clearTable(), reservation ID will also be reset. **Paid** and **cancel** indicate whether the reservation is either paid (1) or unpaid (0); canceled (1) or uncanceled (0). Reservation also contains **fid1** (foreign key)  and **fid2** (foreign key) which can map to the **Flight** table and get the detailed information of the flights. **Username** is the foreign key that references **Users** table to get the information of the user who books the reservation. Also, we also store the total price of the entire itinerary and store it in the reservation table to make it easier to access.

## Choice of Storing in Database/Memory

We need to keep track of the capacity of each flight without changing the Flights table. Thus, when we want to know if we can book the selected flight without going over the capacity, we run the query to get how many **rid** have booked the selected flight and compare the number with the total capacity in the **Flights**.

We stored the **search** result in an **ItineraryArray** which was implemented by a subclass called **itinerary**, since each search result do not need to be stored if the user quit, so we do not need to store them in the database.
## ER diagrams


![enter image description here](https://i.postimg.cc/wB1kKYwN/Untitled-Diagram.png)
