# Quiz Festival System - Request Specification Document
Museum Event Version / Student Project / LAN Operation

---

## 1. System Overview

This system is a quiz festival platform used inside a museum.  
Participants answer the same multiple-choice questions simultaneously.  
A host controls the quiz progress. Scoring and ranking are shown at the end.

---

## 2. Event Context & Constraints

- Usage Location: Inside museum
- Network Environment: Museum local Wi-Fi (LAN)
- Internet Dependency: **Not required**
- Server Location: Museum computer
- Participants: Visitors connected to museum Wi-Fi
- Allowed Devices: Smartphones + PCs
- Estimated Audience: 10–200 (scalable)
- Purpose: Event quiz experience

---

## 3. Roles

### Host
- Controls quiz flow
- Displays questions
- Triggers timers
- Ends quiz
- Shows results

### Participant
- Joins via QR/URL
- Answers questions
- Sees final score and ranking

---

## 4. Functional Requirements

| Function | Description |
|---|---|
| Question Broadcast | Host triggers question distribution |
| Real-time Sync | Participants receive question instantly |
| Answer Input | 4-choice format |
| Timer | 60s per question |
| Score Calculation | Correct = 1 point |
| Ranking Calculation | Only at end |
| Result Display | Personal score + Ranking + Top 3 |
| Player Identification | Auto ID assignment |
| Device Support | PC + Mobile |

---

## 5. Real-time Requirements

| Item | Requirement |
|---|---|
| Sync Method | WebSocket |
| Latency | < 300ms acceptable (LAN) |
| Progress Control | Host triggered |
| Ranking | End of quiz only |
| Timer Source | Server-originated |

---

## 6. Non-functional Requirements

| Category | Requirement |
|---|---|
| Robustness | Users may disconnect and reconnect |
| Performance | ~100 req/sec within LAN acceptable |
| UX | Mobile-first responsive UI |
| Deployment | Local Java server |
| Availability | During event sessions |
| Security | LAN isolation sufficient |

---

## 7. System Architecture

Layers overview:
[Participant Browser (HTML/JS)]
↓ WebSocket + REST
[Java Backend Application]
↓
[Data Storage]

Communication channels:

- WebSocket → real-time quiz events
- REST → join / answer / result retrieval

---

## 8. Network Topology (LAN)
Museum LAN
┌───────────────────────────────┐
│ Museum Wi-Fi (SSID: event) │
└───────┬────────────────┬──────┘
│ │
Host PC (Server) Participants (Phones/PCs)

No external internet dependency.

---

## 9. WebSocket Event Model

### Event Types

| Event | From → To | Description |
|---|---|---|
| `question.start` | Host → Participants | Sends question payload |
| `question.end` | Host → Participants | Ends answering phase |
| `quiz.finish` | Server → Participants | Triggers result phase |
| `score.update` | Server → Participant | Personal score |
| `system.ping` | Bidirectional | Connection check |

---

### Example Question Payload
{
  "type": "question.start",
  "questionId": 12,
  "text": "Which is ...?",
  "options": ["A", "B", "C", "D"],
  "timeLimit": 60
}

## 10. REST API Specification
| Method | Endpoint      | Purpose                           |
| ------ | ------------- | --------------------------------- |
| POST   | `/api/join`   | Join session; returns playerId    |
| POST   | `/api/answer` | Submit answer for question        |
| GET    | `/api/result` | Retrieve ranking + personal score |

Example /api/join Response
{
  "playerId": "u2391"
}

## 11. Database Schema
TABLE players (
  id VARCHAR PRIMARY KEY,
  created_at TIMESTAMP
);

TABLE questions (
  id INT PRIMARY KEY,
  text VARCHAR,
  optionA VARCHAR,
  optionB VARCHAR,
  optionC VARCHAR,
  optionD VARCHAR,
  correct CHAR(1)
);

TABLE answers (
  player_id VARCHAR,
  question_id INT,
  choice CHAR(1),
  correct BOOLEAN,
  answered_at TIMESTAMP,
  PRIMARY KEY(player_id, question_id)
);


## 12. State Machine
Quiz lifecycle:
IDLE → WAIT_JOIN → QUESTION_ACTIVE → QUESTION_CLOSED → RESULT → END


## 13. Sequence (Per Question)

1. Host triggers question
2. Server broadcasts WebSocket event
3. Participants choose answer
4. Server stores correctness until timeout
5. Host moves to next question
6. After last question → result calculated
7. Result distributed to participants

## 14. Scoring & Ranking Rules
- Score = total correct answers
- No time-based bonus
- Ranking = descending score
- Tie-break = join time (ascending)

## 15. Host UI Requirements

Host must display:

Question preview

Start / Next / End controls

Timer

Participant progress indicator

Final ranking view

Buttons:

[Start Quiz]
[Next Question]
[End Quiz]

## 16. Participant UI Requirements

Screens:

Join Screen

Waiting Screen

Answer Screen (with timer)

Result Screen

## 17. Deployment Requirements

Backend: Java

Frontend: HTML + CSS + JavaScript

Local PC server inside LAN

Participants access via LAN URL (e.g., http://192.168.10.20)

No cloud required

## 18. Constraints

Operation restricted to museum Wi-Fi

Only clients connected to LAN may participate

No external authentication required

## 19. Future Extensions (Optional)

Time-based point bonus

Live ranking dashboard

Team competitions

Multi-language support

Offline editor improvements

## 20. Conclusion

This document defines a synchronized museum quiz system operated via LAN.
It supports host-driven real-time question distribution, participant answering, scoring, and final ranking presentation within LAN constraints.