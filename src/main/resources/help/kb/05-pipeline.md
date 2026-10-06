---
routes: [pipeline]
requires: []
---
# Pipeline

The **Pipeline** board shows where each matched candidate stands on each job. Every card is a match report. If the board is empty, run matching on a job to fill it.

## Statuses (columns)
New → Contacted → Shortlisted → Interviewing → Offered → Hired, plus Rejected and Withdrawn. Columns can be collapsed or expanded; long columns have **Load more**.

## Filtering
Pick a **Job** (or **All jobs**) and use **Search candidates**. Click **Refresh** to reload.

## Moving a candidate
You need the "Edit Reports" permission. Without it you can view the board but not move cards.
- **Drag and drop** a card to another column.
- Or open the card menu and choose **Move to…**.
- With the keyboard: focus a card, press Space to pick it up, use the arrow keys to choose a column, Space to drop, Escape to cancel. Enter opens the report.
After a move you can click **Undo**. Each card shows who moved it.

Click a card to open the match report in a side panel.

## Conflicts
If someone else moved the same candidate in the meantime, you see "Someone else moved this candidate in the meantime" and the board updates to show where the candidate is. If a move fails, the card goes back to where it was.

## Automatic moves
- When you email a candidate from their match report, a candidate in **New** moves to **Contacted** automatically. A candidate already further along is never moved back.
- Copilot can move candidates when you ask it (for example "move the top 3 to Shortlisted"). The history shows "by Copilot".
