import React, {useState, useEffect} from 'react';
import {
  SafeAreaView,
  StyleSheet,
  Text,
  TextInput,
  Button,
  View,
} from 'react-native';
import Sound from 'react-native-sound';

// Enable playback in silence mode
Sound.setCategory('Playback');

const App = () => {
  const [k, setK] = useState('10');
  const [m, setM] = useState('7');
  const [n, setN] = useState('5');
  const [isRunning, setIsRunning] = useState(false);
  const [timerStatus, setTimerStatus] = useState('Ready to start.');
  const [elapsedTime, setElapsedTime] = useState(0); // New state for elapsed time

  // Sound objects should be created once and managed
  const bell1 = new Sound('bell1.m4a', Sound.MAIN_BUNDLE, (error) => {
    if (error) {
      console.log('failed to load bell1.m4a', error);
    }
  });

  const bell2 = new Sound('bell2.m4a', Sound.MAIN_BUNDLE, (error) => {
    if (error) {
      console.log('failed to load bell2.m4a', error);
    }
  });

  useEffect(() => {
    let initialDelayTimer: NodeJS.Timeout | null = null;
    let intervalTimer: NodeJS.Timeout | null = null;
    let mainTimer: NodeJS.Timeout | null = null;
    let elapsedTimeInterval: NodeJS.Timeout | null = null; // New timer for elapsed time

    const startTimer = () => {
      setIsRunning(true);
      setTimerStatus('Timer running...');
      setElapsedTime(0); // Reset elapsed time on start

      const kSeconds = parseInt(k, 10) * 1000;
      const mMinutes = parseInt(m, 10) * 60 * 1000;
      const nIntervals = parseInt(n, 10);
      const totalDuration = mMinutes * nIntervals;

      // First bell after K seconds
      initialDelayTimer = setTimeout(() => {
        bell1.play((success) => {
          if (!success) {
            console.log('playback failed due to audio decoding errors for bell1');
          }
        });
        setTimerStatus(`First bell after ${k} seconds.`);

        // Start elapsed time counter after the first bell
        const firstBellTimestamp = Date.now();
        elapsedTimeInterval = setInterval(() => {
          setElapsedTime(Math.floor((Date.now() - firstBellTimestamp) / 1000));
        }, 1000);


        // Subsequent bells every M minutes
        let intervalCount = 1;
        intervalTimer = setInterval(() => {
          if (intervalCount < nIntervals) {
            bell1.play((success) => {
              if (!success) {
                console.log('playback failed due to audio decoding errors for bell1');
              }
            });
            setTimerStatus(`Interval bell ${intervalCount} of ${nIntervals - 1}.`);
            intervalCount++;
          }
        }, mMinutes);
      }, kSeconds);

      // Final bell after M*N minutes
      mainTimer = setTimeout(() => {
        bell2.play((success) => {
          if (!success) {
            console.log('playback failed due to audio decoding errors for bell2');
          }
          // Call stopTimer AFTER bell2 has attempted to play
          stopTimer();
        });
        setTimerStatus(`Final bell after ${m * n} minutes. Timer finished.`);
      }, totalDuration);
    };

    const stopTimer = () => {
      if (initialDelayTimer) clearTimeout(initialDelayTimer);
      if (intervalTimer) clearInterval(intervalTimer);
      if (mainTimer) clearTimeout(mainTimer);
      if (elapsedTimeInterval) clearInterval(elapsedTimeInterval); // Clear elapsed time interval
      setIsRunning(false);
      setTimerStatus('Timer stopped.');
      setElapsedTime(0); // Reset elapsed time on stop
    };

    if (isRunning) {
      startTimer();
    }

    return () => {
      // Release sound resources when component unmounts
      bell1.release();
      bell2.release();
      // Clear any running timers
      if (initialDelayTimer) clearTimeout(initialDelayTimer);
      if (intervalTimer) clearInterval(intervalTimer);
      if (mainTimer) clearTimeout(mainTimer);
      if (elapsedTimeInterval) clearInterval(elapsedTimeInterval);
    };
  }, [isRunning, k, m, n]);

  // Helper to format time as MM:SS
  const formatTime = (totalSeconds: number) => {
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  };

  return (
    <SafeAreaView style={styles.container}>
      <Text style={styles.title}>Meditation Timer</Text>
      <View style={styles.inputContainer}>
        <Text>Initial Delay (K seconds):</Text>
        <TextInput
          style={styles.input}
          value={k}
          onChangeText={setK}
          keyboardType="numeric"
          editable={!isRunning}
        />
      </View>
      <View style={styles.inputContainer}>
        <Text>Interval (M minutes):</Text>
        <TextInput
          style={styles.input}
          value={m}
          onChangeText={setM}
          keyboardType="numeric"
          editable={!isRunning}
        />
      </View>
      <View style={styles.inputContainer}>
        <Text>Number of Intervals (N):</Text>
        <TextInput
          style={styles.input}
          value={n}
          onChangeText={setN}
          keyboardType="numeric"
          editable={!isRunning}
        />
      </View>
      <View style={styles.buttonContainer}>
        <Button title="Start Timer" onPress={() => setIsRunning(true)} disabled={isRunning} />
        <Button title="Stop Timer" onPress={() => setIsRunning(false)} disabled={!isRunning} />
      </View>
      <Text style={styles.status}>{timerStatus}</Text>
      {/* Elapsed time is always visible */}
      <Text style={styles.elapsedTime}>Elapsed: {formatTime(elapsedTime)}</Text>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 20,
  },
  inputContainer: {
    marginBottom: 15,
    width: '80%',
  },
  input: {
    height: 40,
    borderColor: 'gray',
    borderWidth: 1,
    marginTop: 5,
    paddingHorizontal: 10,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    width: '80%',
    marginTop: 20,
  },
  status: {
    marginTop: 20,
    fontSize: 16,
  },
  elapsedTime: { // New style for elapsed time
    marginTop: 10,
    fontSize: 20,
    fontWeight: 'bold',
    color: 'blue',
  },
});

export default App;

