# Trace Analysis: trace.perfetto-trace
## Package: com.mrndstvndv.search.debug
## Activity: SearchActivity
## Search Query: "messenger"
## Date/Time: 2026-07-19 12:20

## Chain of Evidence


### Threads found
- utid=382, tid=32652, name="ndstvndv.search" (main thread), total CPU=307ms, slices=418
- utid=776, tid=2447, name="dv.search.debug" (likely worker), total CPU=4914ms, slices=1474
- utid=813, tid=2509, name="dv.search.debug", total CPU=0.4ms
- utid=783, tid=2472, name="dv.search.debug", total CPU=0.4ms
- utid=820, tid=2514, name="dv.search.debug", total CPU=0.4ms
- utid=1083, tid=2625, name="dv.search.debug", total CPU=0.3ms
- utid=784, tid=2475, name="dv.search.debug", total CPU=0.2ms

Main thread (utid=382) time range: 80600962337349 to 80613088856109
Worker thread (utid=776) time range: 80602707166377 to 80620020053466

### Thread state breakdown

**Main thread (utid=382):**
- Running: 307ms (actual CPU work)
- Runnable (R/R+): 66ms (scheduler delay)
- Sleeping: 21.1s
- Unint. Sleep (D): 4ms

**Worker thread (utid=776):**
- Running: 4914ms (largest CPU consumer)
- Runnable (R/R+): 251ms
- Sleeping: 14.6s
- Unint. Sleep (D): 6ms

### Key CPU consumers (entire system)
1. surfaceflinger (pid 1654): 5104ms - display compositor
2. **dv.search.debug worker (tid 2447): 4914ms - search app worker thread**
3. RenderEngine: 2966ms - GPU rendering pipeline
4. binder:1468_1: 2213ms - surfaceflinger binder
5. Jit thread pool (likely our app): 1815ms - JIT compilation
6. RenderThread (tid 2507, likely our app): 1077ms - rendering
7. ndroid.systemui: 1985ms - system UI

### App threads CPU usage (com.mrndstvndv.search.debug)
- Worker thread (tid 2447): 4914ms - dominant CPU consumer
- Main thread (tid 32652): 307ms - mostly sleeping/waiting
- Jit thread pool (tid 2479): 1815ms - JIT compiling
- RenderThread (tid 2507): 1077ms - rendering
- Other pool threads: ~2ms total

### Scheduling analysis (worker thread)
- Average scheduling latency: 1.8ms (reasonable)
- Max scheduling latency: 4.5ms (no significant contention)
- CPU affinity: Mostly CPU 7 (big core) at 2.67 GHz
- No CPU frequency throttling detected

### Wake sources for worker thread
- Jit thread pool: 3580 wakeups - JIT driving work
- RenderThread: 1043 wakeups - rendering driving work
- kworker: ~300 wakeups
- binder:1654 (surfaceflinger): ~570 wakeups
