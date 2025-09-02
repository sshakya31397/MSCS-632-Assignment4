# Employee Scheduling Application

## Overview
This project is a weekly shift scheduling system for a company operating 7 days a week with **morning**, **afternoon**, and **evening** shifts.  
It demonstrates the use of **conditionals, loops, and branching** in two different programming languages: **Python** and **Java**.

## Features
- Collects employee names and daily shift preferences (single or ranked).
- Ensures:
  - No employee works more than **one shift per day**.
  - No employee works more than **5 days per week**.
  - At least **2 employees per shift per day**.
- Resolves conflicts when preferred shifts are full by reassigning or deferring to the next day.
- Randomly assigns additional employees when shifts fall below the minimum coverage.
- Outputs a readable weekly schedule table.
- Supports ranked preferences (bonus feature).
- Reports unmet coverage if staffing is insufficient.

## Running the Code

### Python
1. Navigate to the `python/` folder.
2. Run the program:
   ```bash
   python scheduler.py

   javac Main.java
