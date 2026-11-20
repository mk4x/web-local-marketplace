insert into categories (path, label)
values
('home', 'Home'),
('furniture', 'Furniture'),
('electronics', 'Electronics'),
('clothing', 'Clothing'),
('books', 'Books'),
('vehicles', 'Vehicles'),
('vehicles.bikes', 'Bikes')
on conflict do nothing
