def lostlink_prediction(reports):
    # reports: list of dicts with 'timestamp', 'rssi', 'nodeInfo'
    if not reports:
        return "Unknown Location"
    
    # Sort by recent and strong RSSI
    sorted_reports = sorted(reports, key=lambda x: (x['timestamp'], x['rssi']), reverse=True)
    best = sorted_reports[0]
    
    if best['rssi'] > -60:
        return f"Very Close to {best.get('nodeInfo', 'Mesh Node')}"
    elif best['rssi'] > -80:
        return f"Near {best.get('nodeInfo', 'Mesh Node')}"
    else:
        return f"Distantly seen by {best.get('nodeInfo', 'Mesh Node')}"

def last_seen_estimator(reports):
    if not reports:
        return 0
    # Return weighted average timestamp
    weights = []
    times = []
    for r in reports:
        # RSSI weight: -40 -> 1.0, -90 -> 0.1
        w = max(0.1, (r['rssi'] + 100) / 60.0)
        weights.append(w)
        times.append(r['timestamp'])
    
    if not weights: return max(r['timestamp'] for r in reports)
    return sum(t * w for t, w in zip(times, weights)) / sum(weights)

def recovery_confidence(rssi_values):
    if not rssi_values:
        return 0.0
    avg_rssi = sum(rssi_values) / len(rssi_values)
    # -50 or better -> 1.0, -100 or worse -> 0.0
    conf = (avg_rssi + 100) / 50.0
    return max(0.0, min(1.0, conf))

def priority_score(text, has_attachment):
    text = text or ""
    score = min(len(text) // 2, 40)
    lowered = text.lower()
    if any(word in lowered for word in ("urgent", "important", "emergency", "asap")):
        score += 35
    if has_attachment:
        score += 20
    if len(text) < 12 and not has_attachment:
        score -= 5
    return max(0, min(int(score), 100))

def smart_reply(text):
    text = (text or "").strip().lower()
    if not text:
        return "File received."
    if any(word in text for word in ("hello", "hi", "hey")):
        return "Hi. Message received locally."
    if any(word in text for word in ("thanks", "thank you")):
        return "You're welcome."
    if any(word in text for word in ("urgent", "emergency", "help")):
        return "Understood. Keeping this at high priority."
    if len(text) > 80:
        return "Noted. I saved the longer update locally."
    return "Got it. Stored offline."
