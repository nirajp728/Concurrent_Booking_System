import React, { useState, useEffect, useCallback } from 'react';
import { Armchair, Clock, CheckCircle2, AlertTriangle, Film } from 'lucide-react';

// Simulate a random user session ID on launch
const CURRENT_USER_ID = `user_${Math.random().toString(36).substring(2, 9)}`;

// Target your Spring Boot backend explicitly across the local Wi-Fi network
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://192.168.0.239:8080";

export default App;

function App() {
  const [movies, setMovies] = useState([]);
  const [selectedMovie, setSelectedMovie] = useState(null);
  const [seats, setSeats] = useState([]);
  const [activeSession, setActiveSession] = useState(null); // { sessionId, seatId, expiresAt }
  const [timeLeft, setTimeLeft] = useState(0);
  const [errorMessage, setErrorMessage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  // 1. Fetch available movies on load
  useEffect(() => {
    fetch(`${API_BASE_URL}/api/movies`)
      .then((res) => res.json())
      .then((data) => {
        setMovies(data);
        if (data.length > 0) setSelectedMovie(data[0]);
      })
      .catch(() => setErrorMessage("Could not load movies database. Check if backend is running."));
  }, []);

  // 2. Fetch current seat maps for the selected movie
  const refreshSeats = useCallback(() => {
    if (!selectedMovie) return;
    fetch(`${API_BASE_URL}/api/movies/${selectedMovie.id}/seats`)
      .then((res) => res.json())
      .then((data) => setSeats(data))
      .catch(() => console.error("Error updates fetching seat states."));
  }, [selectedMovie]);

  // Long-poll or background sync the seat map layout every 2 seconds
  useEffect(() => {
    refreshSeats();
    const interval = setInterval(refreshSeats, 500);
    return () => clearInterval(interval);
  }, [refreshSeats]);

  // 3. Countdown timer logic for active seat holds
  useEffect(() => {
    if (!activeSession) return;

    const calculateTime = () => {
      const diff = Math.max(0, Math.floor((new Date(activeSession.expiresAt) - new Date()) / 1000));
      setTimeLeft(diff);

      if (diff === 0) {
        setActiveSession(null);
        setErrorMessage("Your 2-minute seat hold has expired.");
        refreshSeats();
      }
    };

    calculateTime();
    const timer = setInterval(calculateTime, 1000);
    return () => clearInterval(timer);
  }, [activeSession, refreshSeats]);

  // 4. ACTION: Place a temporary lock (Hold) on a seat
  const handleHoldSeat = async (seatId) => {
    if (activeSession) {
      setErrorMessage("You can only hold one seat at a time! Release your current seat first.");
      return;
    }

    setErrorMessage("");
    setSuccessMessage("");

    try {
      const response = await fetch(`${API_BASE_URL}/api/movies/${selectedMovie.id}/seats/${seatId}/hold`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: CURRENT_USER_ID })
      });

      if (response.status === 409) {
        setErrorMessage("Too slow! That seat was locked by another user just now.");
        refreshSeats();
        return;
      }

      if (!response.ok) throw new Error();

      const data = await response.json();
      setActiveSession({
        sessionId: data.sessionId,
        seatId: data.seatId,
        expiresAt: data.expiresAt
      });
      refreshSeats();
    } catch {
      setErrorMessage("System error processing lock. Try again.");
    }
  };

  // 5. ACTION: Complete the checkout sequence (Confirm)
  const handleConfirmBooking = async () => {
    if (!activeSession) return;

    try {
      const response = await fetch(`${API_BASE_URL}/api/sessions/${activeSession.sessionId}/confirm`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: CURRENT_USER_ID })
      });

      if (!response.ok) throw new Error();

      setSuccessMessage(`Success! Seat ${activeSession.seatId} is permanently reserved.`);
      setActiveSession(null);
      refreshSeats();
    } catch {
      setErrorMessage("Failed to secure confirmation processing.");
    }
  };

  // 6. ACTION: Abandon and unlock the reservation explicitly
  const handleReleaseBooking = async () => {
    if (!activeSession) return;

    try {
      await fetch(`${API_BASE_URL}/api/sessions/${activeSession.sessionId}`, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userId: CURRENT_USER_ID })
      });
      setActiveSession(null);
      refreshSeats();
    } catch {
      setErrorMessage("Could not cleanly release lock context.");
    }
  };

  // Formulate rendering layout calculations
  const totalRows = selectedMovie ? selectedMovie.rows : 0;
  const seatsPerRow = selectedMovie ? selectedMovie.seatsPerRow : 0;

  return (
    <div className="min-h-screen p-6 md:p-12 max-w-6xl mx-auto">
      {/* Upper Context Metric */}
      <header className="flex flex-col md:flex-row justify-between items-start md:items-center border-b border-slate-800 pb-6 mb-8 gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight bg-gradient-to-r from-teal-400 to-emerald-400 bg-clip-text text-transparent">
            Concurrent Seat Multiplex Engine
          </h1>
          <p className="text-slate-400 text-sm mt-1">
            Simulating Distributed Pessimistic Isolation Locks via Redis
          </p>
        </div>
        <div className="bg-slate-800/60 px-4 py-2 rounded-lg border border-slate-700 text-xs text-slate-300">
          Your Session Hash: <span className="font-mono text-teal-400 font-semibold">{CURRENT_USER_ID}</span>
        </div>
      </header>

      {/* Notifications banner system */}
      {errorMessage && (
        <div className="bg-red-500/10 border border-red-500/20 text-red-400 p-4 rounded-xl mb-6 flex items-center gap-3">
          <AlertTriangle className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{errorMessage}</span>
        </div>
      )}
      {successMessage && (
        <div className="bg-emerald-500/10 border border-emerald-500/20 text-emerald-400 p-4 rounded-xl mb-6 flex items-center gap-3">
          <CheckCircle2 className="w-5 h-5 flex-shrink-0" />
          <span className="text-sm font-medium">{successMessage}</span>
        </div>
      )}

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Left Side Controls: Movie Select & Status Panel */}
        <div className="space-y-6">
          <section className="bg-slate-900/50 border border-slate-800 rounded-2xl p-6">
            <h2 className="text-lg font-bold flex items-center gap-2 mb-4 text-slate-200">
              <Film className="w-5 h-5 text-teal-400" /> Choose Movie Frame
            </h2>
            <div className="space-y-2">
              {movies.map((movie) => (
                <button
                  key={movie.id}
                  onClick={() => {
                    if (activeSession) handleReleaseBooking();
                    setSelectedMovie(movie);
                  }}
                  className={`w-full text-left p-4 rounded-xl border transition-all ${
                    selectedMovie?.id === movie.id
                      ? 'bg-teal-500/10 border-teal-500 text-teal-300 font-semibold'
                      : 'bg-slate-800/40 border-slate-800 text-slate-400 hover:bg-slate-800/80 hover:text-slate-200'
                  }`}
                >
                  {movie.title}
                  <div className="text-xs font-normal opacity-80 mt-1">
                    Dimensions: {movie.rows} rows × {movie.seatsPerRow} seats
                  </div>
                </button>
              ))}
            </div>
          </section>

          {/* Active Locking Panel details */}
          {activeSession && (
            <section className="bg-amber-500/10 border border-amber-500/20 rounded-2xl p-6 relative overflow-hidden animate-pulse">
              <h2 className="text-lg font-bold flex items-center gap-2 mb-3 text-amber-400">
                <Clock className="w-5 h-5" /> Lock Allocation Lease
              </h2>
              <p className="text-slate-300 text-sm">
                Seat <span className="font-bold text-amber-300">{activeSession.seatId}</span> is temporarily reserved for you in the Redis distributed thread.
              </p>
              
              <div className="my-5 text-4xl font-black font-mono text-center tracking-widest text-amber-400">
                {Math.floor(timeLeft / 60)}:{(timeLeft % 60).toString().padStart(2, '0')}
              </div>

              <div className="flex gap-3">
                <button
                  onClick={handleConfirmBooking}
                  className="flex-1 bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-sm py-2.5 px-4 rounded-xl transition"
                >
                  Confirm Purchase
                </button>
                <button
                  onClick={handleReleaseBooking}
                  className="bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm py-2.5 px-4 rounded-xl transition"
                >
                  Cancel
                </button>
              </div>
            </section>
          )}
        </div>

        {/* Right Side Matrix Frame Layout Map */}
        <div className="lg:col-span-2 bg-slate-900/30 border border-slate-800 rounded-2xl p-6 flex flex-col items-center justify-between min-h-[400px]">
          <div className="w-full">
            <h2 className="text-lg font-bold mb-6 text-slate-200">Seat Grid Interactive Map</h2>
            
            {/* Movie Screen Indicator */}
            <div className="w-3/4 mx-auto bg-gradient-to-b from-teal-500/30 to-transparent h-6 rounded-t-full text-center text-[10px] uppercase font-bold tracking-widest text-teal-400/80 mb-12 shadow-xl shadow-teal-500/5">
              Screen Surface Location
            </div>

            {/* Seat Arrangement Grid */}
            <div className="flex flex-col gap-3 items-center overflow-x-auto w-full pb-4">
              {Array.from({ length: totalRows }).map((_, rowIndex) => {
                const rowLetter = String.fromCharCode(65 + rowIndex);
                return (
                  <div key={rowLetter} className="flex gap-3 items-center">
                    <span className="text-xs font-mono text-slate-500 w-4 font-bold text-center">{rowLetter}</span>
                    {Array.from({ length: seatsPerRow }).map((_, seatIdx) => {
                      const seatId = `${rowLetter}${seatIdx + 1}`;
                      
                      const seatState = seats.find((s) => s.seatId === seatId);
                      const isHeldByMe = activeSession?.seatId === seatId;
                      const isConfirmed = seatState?.confirmed;
                      const isHeldByOthers = seatState?.booked && !isConfirmed && !isHeldByMe;

                      let colorClass = "bg-slate-800 hover:bg-slate-700 text-slate-400 border border-slate-700 hover:scale-105";
                      if (isHeldByMe) colorClass = "bg-amber-500 text-slate-950 border-amber-400 shadow-lg shadow-amber-500/20 animate-none scale-105";
                      else if (isConfirmed) colorClass = "bg-emerald-600/30 text-emerald-400 border-emerald-500/30 cursor-not-allowed";
                      else if (isHeldByOthers) colorClass = "bg-red-500/20 text-red-400 border-red-500/20 cursor-not-allowed pattern-stripe animate-pulse";

                      return (
                        <button
                          key={seatId}
                          disabled={isConfirmed || isHeldByOthers}
                          onClick={() => handleHoldSeat(seatId)}
                          className={`w-10 h-10 rounded-xl flex flex-col items-center justify-center text-[10px] font-bold font-mono transition-all ${colorClass}`}
                          title={`Seat ${seatId} ${isHeldByOthers ? '(Held by another user)' : ''}`}
                        >
                          <Armchair className="w-4 h-4 mb-0.5" />
                          {seatId}
                        </button>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          </div>

          {/* Guide legend metadata parameters */}
          <div className="flex flex-wrap gap-4 border-t border-slate-800 w-full pt-6 justify-center text-xs text-slate-400">
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 bg-slate-800 border border-slate-700 rounded" /> Available
            </div>
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 bg-amber-500 rounded shadow" /> Your Hold
            </div>
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 bg-red-500/20 border border-red-500/30 rounded animate-pulse" /> Someone Else's Hold
            </div>
            <div className="flex items-center gap-2">
              <div className="w-4 h-4 bg-emerald-600/30 border border-emerald-500/30 rounded" /> Confirmed Sale
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}