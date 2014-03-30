RKFRead
-------

An Android NFC-app for reading travelcards (purses, period cards, etc) made by 
"Resekortsföreningen i Norden" used in Sweden and possibly Denmark and Norway.

The app tries to parse the card data as per the standards published in 2002 and
testing and reverse engineering of various samples of travel cards. It is 
primarely targeting enthusiasts who wants to know more about their cards but 
should also be easy to use and useful for travellers wanting to see their 
current purse or period card information.
The standards are no longer public but you can find the latest published ones at
http://web.archive.org/web/20050307215132/http://www.resekortsforeningen.se/specifikationer.htm

To use this app you must have a Android phone with NXP NFC-controller (Broadcom
seems to not work) so if you are unable to get card data from a known to work 
card (list below) you should look into if your hardware could be the problem.
The interface should be self-explanatory and you should only need to present
a RKF compliant card to the NFC reader for it to read it and present the 
information found with a purse summary followed by overview information about
tickets and period cards. A Debug-button exist to show _all_ information that
could be extracted.


Known to work cards
-------------------

* Skånetrafiken JOJO cards
* Länstrafiken kronoberg cards (purse only)
* Norrbotten busskort (purse only)
* SL cards
* Västtrafiken cards (purse only)

Known not to work
-----------------

* Gotland travelcards (United Ticket from Estonia and not RKF standard)

Licence
-------

This application was developed by Henning Norén in 2014.
It is free software and licensed under the GNU General Public Licence v3.

The standards implemented by this application is owned by Resekortsföreningen i Norden.

Bugs and contact
----------------

Bug reports and reports of problems with cards can be sent to 
henning (dot) noren (at) gmail (dot) com 
