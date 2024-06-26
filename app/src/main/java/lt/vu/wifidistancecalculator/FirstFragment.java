package lt.vu.wifidistancecalculator;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.ScanResultsCallback;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import lt.vu.wifidistancecalculator.databinding.FragmentFirstBinding;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private WifiManager wifiManager;
    //안드로이드로 와이파이를 관리하는 라이브러리로 현재 와이파이를 읽을때 startscan이라는 라이브러리를 사용하는데 안드로이드 10이상에서는
    //해당 메소드를 사용할 수 없다. 대신하여 registerScanResultsCallback, ScanResultsCallback를 사용한다.
    private List<String> scanResults = new ArrayList<>();
    private BroadcastReceiver wifiScanReceiver;

    private List<String> desiredBSSIDs = new ArrayList<>();

    private ScanResultsCallback scanResultsCallback;
    //안드로이드 11버전 이상에서 와이파이와 통신을 하기 위해 해당 객체가 필요

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        wifiManager = (WifiManager) requireActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        setupWifiScanMethod();

        return binding.getRoot();
    }

    private void setupWifiScanMethod() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 이상에서는 ScanResultsCallback를 사용
            scanResultsCallback = new ScanResultsCallback() {
                @Override
                public void onScanResultsAvailable() {
                    scanSuccess();
                }
            };
            wifiManager.registerScanResultsCallback(requireContext().getMainExecutor(), scanResultsCallback);
        } else {
            // Android 10 이하에서는 BroadcastReceiver를 사용
            IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            wifiScanReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                    if (success) {
                        scanSuccess();
                    }
                }
            };
            requireActivity().getApplicationContext().registerReceiver(wifiScanReceiver, intentFilter);
        }
    }


    private void scanWifi() {
        if (!wifiManager.isWifiEnabled()) { //와이파이 켜져있는지 확인
            showSnackbar("WiFi is disabled ...", BaseTransientBottomBar.LENGTH_LONG);
            return;
        }
        boolean success = wifiManager.startScan();
        if (!success) { //와이파이 스캔이 되었는지 확인
            // Scan failure handling
            showSnackbar("Scan initiation failed", BaseTransientBottomBar.LENGTH_SHORT);
        }
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            });

    private void checkForLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireActivity().getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void scanSuccess() {
        checkForLocationPermission();
        List<ScanResult> results = wifiManager.getScanResults();
//        showSnackbar("Result size:" + results.size(), BaseTransientBottomBar.LENGTH_LONG);

        List<String> stringResults = results.stream()
                .filter(scanResult -> StringUtils.isNotEmpty(scanResult.SSID))
                .sorted(Comparator.comparing(scanResult -> scanResult.level, Comparator.reverseOrder()))
                .map(scanResult -> "SSID: " + scanResult.SSID + ", BSSID: " + scanResult.BSSID + " RSSI:" + scanResult.level)
                .collect(Collectors.toList());
        scanResults = stringResults;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, stringResults);
        binding.wifiList.setAdapter(adapter);
//        saveToFile(); //이 부분 주석 시 스캔하자마자 저장 안함, 저장 버튼 누를때만 저장 되는지 확인 필요
//        scanWifi(); //현재 이 부분때문에 스캔이 계속 되니깐 이후에 주석처리 해서 버튼을 눌렀을 때에만 되게 가능
    }

    private void saveToFile() {
        // EditText에서 사용자 입력 가져오기
        String buildingName = binding.buildingNameEdit.getText().toString();
        String floorNumber = binding.floorNumberEdit.getText().toString();
        String nodeNumber = binding.nodeNumberEdit.getText().toString();

        // 입력값 검증
        if (buildingName.isEmpty() || floorNumber.isEmpty() || nodeNumber.isEmpty()) {
            showSnackbar("모든 필드를 입력해주세요.", BaseTransientBottomBar.LENGTH_LONG);
            return;
        }

        // 스캔 결과 중 원하는 BSSID만 필터링
        String filteredWifiInfo = scanResults.stream()
//                .filter(scanInfo -> desiredBSSIDs.stream().anyMatch(scanInfo::contains))
                .collect(Collectors.joining(";"));

        // 파일에 저장할 데이터 생성
        String dataToSave = String.format("건물명: %s, 층수: %s, 노드 번호: %s\n와이파이 정보: %s\n\n",
                buildingName, floorNumber, nodeNumber, filteredWifiInfo);

        // 파일에 데이터 쓰기
        if (writeFileOnInternalStorage("data.txt", dataToSave)) {
            // 파일 저장이 성공하면 사용자에게 알림
            showSnackbar("저장되었습니다", BaseTransientBottomBar.LENGTH_SHORT);
        } else {
            // 파일 저장에 실패한 경우
            showSnackbar("저장에 실패했습니다", BaseTransientBottomBar.LENGTH_SHORT);
        }
    }


    public boolean writeFileOnInternalStorage(String sFileName, String sBody) {
        try {
            // 앱 전용 외부 저장소 디렉토리에 mydir 디렉토리를 생성합니다.
            File storageDir = new File(getContext().getExternalFilesDir(null), "mydir");
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            // 파일을 생성하고 데이터를 씁니다.
            File file = new File(storageDir, sFileName);
            FileWriter writer = new FileWriter(file, true);
            writer.append(sBody);
            writer.flush();
            writer.close();
            return true; // 파일 저장 성공
        } catch (Exception e) {
            e.printStackTrace();
            return false; // 파일 저장 실패
        }
    }


    private void showSnackbar(String text, int length) {
        Snackbar.make(requireActivity().findViewById(android.R.id.content), text, length)
                .setAction("Action", null).show();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.scanBtn.setOnClickListener(v -> scanWifi());
        binding.saveBtn.setOnClickListener(v -> saveToFile());
        binding.findBtn.setOnClickListener(v -> determineCurrentLocation());
        view.findViewById(R.id.dataCheckBtn).setOnClickListener(v -> {
            NavHostFragment.findNavController(FirstFragment.this)
                    .navigate(R.id.action_FirstFragment_to_SecondFragment);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && scanResultsCallback != null) {
            wifiManager.unregisterScanResultsCallback(scanResultsCallback);
        } else if (wifiScanReceiver != null) {
            requireActivity().getApplicationContext().unregisterReceiver(wifiScanReceiver);
        }
        binding = null;
    }

    private int[] scanCurrentWifiRssi() {
        checkForLocationPermission();
        scanWifi();
        int[] rssiValues = new int[desiredBSSIDs.size()];
        Arrays.fill(rssiValues, 0); // 모든 값을 0으로 초기화

        List<ScanResult> scanResults = wifiManager.getScanResults();
        for (int i = 0; i < desiredBSSIDs.size(); i++) {
            for (ScanResult result : scanResults) {
                if (result.BSSID.equals(desiredBSSIDs.get(i))) {
                    rssiValues[i] = result.level;
                    break;
                }
            }
        }
        Log.d("AppLog", "rssiValues4 : " + rssiValues[4]);
        Log.d("AppLog", "rssiValues5 : " + rssiValues[5]);
        return rssiValues;
    }

    private void setDesiredBSSIDs(){
        try {
            Log.d("AppLog", "00");
            File file = new File(getContext().getExternalFilesDir(null), "mydir/bssiddata.txt");
            Log.d("AppLog", "01");
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;
            Log.d("AppLog", "02");
            while ((line = br.readLine()) != null) {
                if (line.isEmpty() || !line.contains(":")) continue;
                Log.d("AppLog", "03");
                desiredBSSIDs.add(line);
                Log.d("AppLog", "04");
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void determineCurrentLocation() {
        Log.d("AppLog", "0");
        setDesiredBSSIDs();
        Log.d("AppLog", "1");
        int[] currentRssiValues = scanCurrentWifiRssi();
        Log.d("AppLog", "2");
        String bestMatch = "";
        int minDifference = Integer.MAX_VALUE;
        Log.d("AppLog", "3");
        try {
            File file = new File(getContext().getExternalFilesDir(null), "mydir/ssiddata.txt");
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line;

            while ((line = br.readLine()) != null) {
                Log.d("AppLog", "line : " + line);
                if (line.isEmpty() || !line.contains("건물명: ")) continue;

                String buildingInfo = line;
                Log.d("AppLog", "buildingInfo : " + buildingInfo);

                line = br.readLine();
                String[] parts = line.split("와이파이 정보: ");
                if (parts.length < 2) continue;
                String[] wifiInfos = parts[1].split(";");

                // BSSID 별 RSSI 값을 임시로 저장할 배열
                int[] tempRssiValues = new int[desiredBSSIDs.size()];
                Arrays.fill(tempRssiValues, 0); // 기본값으로 초기화

                for (String wifiInfo : wifiInfos) {
                    String[] wifiParts = wifiInfo.split(", ");
                    String bssid = wifiParts[1].split(" ")[1];
                    Log.d("AppLog", "bssid : " + bssid);
                    Log.d("AppLog", "wifiParts 0: " + wifiParts[0]);
                    Log.d("AppLog", "wifiParts 1: " + wifiParts[1]);
                    int rssi = Integer.parseInt(wifiParts[1].split("RSSI:")[1]);
                    Log.d("AppLog", "rssi 오류: " + rssi);
                    int index = desiredBSSIDs.indexOf(bssid);
                    if (index != -1) {
                        tempRssiValues[index] = rssi;
                    }
                }

                // 차이를 계산
                int difference = 0;
                for (int i = 0; i < currentRssiValues.length; i++) {
                    difference += Math.pow(currentRssiValues[i] - tempRssiValues[i], 2);
                }
                Log.d("AppLog", "difference 오류: " + difference);
                if (difference < minDifference) {
                    minDifference = difference;
                    bestMatch = buildingInfo;
                }
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!bestMatch.isEmpty()) {
            showSnackbar(bestMatch, BaseTransientBottomBar.LENGTH_LONG);
        } else {
            showSnackbar("위치를 파악할 수 없습니다.", BaseTransientBottomBar.LENGTH_LONG);
        }
    }


    private void showFileContent() {
        // 외부 저장소에서 파일 읽기
        File file = new File(getContext().getExternalFilesDir(null), "mydir/data.txt");
        StringBuilder fileContent = new StringBuilder();
        showSnackbar("1", BaseTransientBottomBar.LENGTH_LONG);
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            showSnackbar("2", BaseTransientBottomBar.LENGTH_LONG);
            while ((line = br.readLine()) != null) {
                fileContent.append(line).append("\n");
            }
            // 스낵바 표시
            Snackbar.make(requireView(), fileContent.toString(), Snackbar.LENGTH_LONG).show();
        } catch (IOException e) {
            Log.e("FileReadError", "파일을 읽는 중 오류 발생", e);
            Snackbar.make(requireView(), "파일을 읽을 수 없습니다.", Snackbar.LENGTH_LONG).show();
        }
    }


}