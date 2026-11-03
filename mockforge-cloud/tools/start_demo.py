"""Wait for the servers, check the database through the API, and open the shop and API explorer."""
import os
import sys
import time
import urllib.request
import webbrowser



def wait_for(url, seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return True
        except OSError:
            pass
        time.sleep(1)
    return False


print('Waiting for the backend and MySQL connection...')
if not wait_for('http://localhost:8080/api/workspaces', 90):
    print('Not ready. Start your MySQL service and check the backend log window.')
    print('Database credentials come from application.properties or DB_USERNAME / DB_PASSWORD.')
    sys.exit(1)

print('\nSHOP: http://localhost:8080/shop.html')
print('API EXPLORER: http://localhost:8080/docs.html')
print('1. Create a workspace and define a mock endpoint.')
print('2. Use the request panel to choose a method, enter a path and send a request.')
print('3. The worker prints LOG SAVED after inserting the request into MySQL.')
print('The frontend has no separate process; Spring Boot serves its HTML, CSS and JavaScript.')
print('Keep the two server windows open. Press Ctrl+C in each one to stop.')
if os.environ.get('MOCKFORGE_NO_BROWSER') != '1':
    webbrowser.open('http://localhost:8080/shop.html')
    webbrowser.open_new_tab('http://localhost:8080/docs.html')
