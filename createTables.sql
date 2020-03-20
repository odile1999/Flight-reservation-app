CREATE TABLE Users (
  username varchar(20) PRIMARY KEY,
  passwordHash varbinary(20),
  passwordSalt varbinary(20),
  balance int
);

CREATE TABLE Reservations (
  rid int primary key identity(1,1) not null,
  paid int,
  cancel int,
  fid1 int,
  fid2 int,
  username varchar(20),
  price int
);