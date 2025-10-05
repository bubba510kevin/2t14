// MyKmdfDriver.c
// Minimal KMDF driver demonstrating device creation, read/write, ioctl.
// Build as KMDF driver (WDK).

#include <ntddk.h>
#include <wdf.h>

#define NT_DEVICE_NAME      L"\\Device\\MyKmfdDevice"
#define DOS_DEVICE_NAME     L"\\DosDevices\\MyKmfd"
#define IOCTL_MY_SIMPLE     CTL_CODE(FILE_DEVICE_UNKNOWN, 0x800, METHOD_BUFFERED, FILE_READ_DATA | FILE_WRITE_DATA)

DRIVER_INITIALIZE DriverEntry;
EVT_WDF_DRIVER_DEVICE_ADD MyEvtDeviceAdd;
EVT_WDF_IO_QUEUE_IO_READ MyEvtIoRead;
EVT_WDF_IO_QUEUE_IO_WRITE MyEvtIoWrite;
EVT_WDF_IO_QUEUE_IO_DEVICE_CONTROL MyEvtIoDeviceControl;
EVT_WDF_OBJECT_CONTEXT_CLEANUP MyDriverContextCleanup;

typedef struct _DEVICE_CONTEXT {
    ULONG64 TotalBytesWritten;
} DEVICE_CONTEXT;

WDF_DECLARE_CONTEXT_TYPE_WITH_NAME(DEVICE_CONTEXT, DeviceGetContext)

NTSTATUS
DriverEntry(
    _In_ PDRIVER_OBJECT  DriverObject,
    _In_ PUNICODE_STRING RegistryPath
)
{
    WDF_DRIVER_CONFIG config;
    NTSTATUS status;

    WDF_DRIVER_CONFIG_INIT(&config, MyEvtDeviceAdd);
    config.EvtDriverUnload = NULL; // optional

    KdPrint(("MyKmfd: DriverEntry\n"));

    status = WdfDriverCreate(DriverObject, RegistryPath, WDF_NO_OBJECT_ATTRIBUTES, &config, WDF_NO_HANDLE);
    if (!NT_SUCCESS(status)) {
        KdPrint(("MyKmfd: WdfDriverCreate failed 0x%X\n", status));
    }

    return status;
}

NTSTATUS
MyEvtDeviceAdd(
    _In_    WDFDRIVER       Driver,
    _Inout_ PWDFDEVICE_INIT DeviceInit
)
{
    UNREFERENCED_PARAMETER(Driver);
    NTSTATUS status;
    WDFDEVICE device;
    WDF_OBJECT_ATTRIBUTES deviceAttributes;
    WDF_IO_QUEUE_CONFIG ioQueueConfig;
    PDEVICE_CONTEXT devContext;
    UNICODE_STRING ntName, dosName;

    KdPrint(("MyKmfd: EvtDeviceAdd\n"));

    // Set device type and characteristics
    WdfDeviceInitSetDeviceType(DeviceInit, FILE_DEVICE_UNKNOWN);

    WDF_OBJECT_ATTRIBUTES_INIT_CONTEXT_TYPE(&deviceAttributes, DEVICE_CONTEXT);

    RtlInitUnicodeString(&ntName, NT_DEVICE_NAME);
    status = WdfDeviceInitAssignName(DeviceInit, &ntName);
    if (!NT_SUCCESS(status)) {
        KdPrint(("MyKmfd: AssignName failed 0x%X\n", status));
        return status;
    }

    status = WdfDeviceCreate(&DeviceInit, &deviceAttributes, &device);
    if (!NT_SUCCESS(status)) {
        KdPrint(("MyKmfd: WdfDeviceCreate failed 0x%X\n", status));
        return status;
    }

    // Create symbolic link for user-mode: \\.\MyKmfd -> \\Device\\MyKmfdDevice
    RtlInitUnicodeString(&dosName, DOS_DEVICE_NAME);
    status = WdfDeviceCreateSymbolicLink(device, &dosName);
    if (!NT_SUCCESS(status)) {
        KdPrint(("MyKmfd: CreateSymbolicLink failed 0x%X\n", status));
        return status;
    }

    devContext = DeviceGetContext(device);
    devContext->TotalBytesWritten = 0;

    // Configure default I/O queue (sequential, handles Read/Write/DeviceControl)
    WDF_IO_QUEUE_CONFIG_INIT_DEFAULT_QUEUE(&ioQueueConfig, WdfIoQueueDispatchSequential);
    ioQueueConfig.EvtIoRead = MyEvtIoRead;
    ioQueueConfig.EvtIoWrite = MyEvtIoWrite;
    ioQueueConfig.EvtIoDeviceControl = MyEvtIoDeviceControl;

    status = WdfIoQueueCreate(device, &ioQueueConfig, WDF_NO_OBJECT_ATTRIBUTES, WDF_NO_HANDLE);
    if (!NT_SUCCESS(status)) {
        KdPrint(("MyKmfd: WdfIoQueueCreate failed 0x%X\n", status));
        return status;
    }

    KdPrint(("MyKmfd: Device created successfully\n"));
    return STATUS_SUCCESS;
}

VOID
MyEvtIoRead(
    _In_ WDFQUEUE   Queue,
    _In_ WDFREQUEST Request,
    _In_ size_t     Length
)
{
    UNREFERENCED_PARAMETER(Queue);
    NTSTATUS status;
    const char reply[] = "Hello from kernel!";
    size_t toCopy = min(Length, sizeof(reply));
    void* buffer = NULL;
    size_t bufLen = 0;

    KdPrint(("MyKmfd: EvtIoRead length=%Iu\n", Length));

    status = WdfRequestRetrieveOutputBuffer(Request, toCopy, &buffer, &bufLen);
    if (!NT_SUCCESS(status)) {
        WdfRequestComplete(Request, status);
        return;
    }

    RtlCopyMemory(buffer, reply, toCopy);
    WdfRequestCompleteWithInformation(Request, STATUS_SUCCESS, toCopy);
}

VOID
MyEvtIoWrite(
    _In_ WDFQUEUE   Queue,
    _In_ WDFREQUEST Request,
    _In_ size_t     Length
)
{
    UNREFERENCED_PARAMETER(Queue);
    NTSTATUS status;
    void* buffer = NULL;
    size_t bufLen = 0;
    WDFDEVICE dev = WdfIoQueueGetDevice(Queue);
    PDEVICE_CONTEXT ctx = DeviceGetContext(dev);

    KdPrint(("MyKmfd: EvtIoWrite length=%Iu\n", Length));

    status = WdfRequestRetrieveInputBuffer(Request, Length, &buffer, &bufLen);
    if (!NT_SUCCESS(status)) {
        WdfRequestComplete(Request, status);
        return;
    }

    // For demo, we just count bytes written. Do NOT attempt user pointer deref beyond bufLen.
    ctx->TotalBytesWritten += (ULONG64)bufLen;

    KdPrint(("MyKmfd: Received %Iu bytes. Total written: %I64u\n", bufLen, ctx->TotalBytesWritten));

    WdfRequestCompleteWithInformation(Request, STATUS_SUCCESS, bufLen);
}

VOID
MyEvtIoDeviceControl(
    _In_ WDFQUEUE   Queue,
    _In_ WDFREQUEST Request,
    _In_ size_t     OutputBufferLength,
    _In_ size_t     InputBufferLength,
    _In_ ULONG      IoControlCode
)
{
    UNREFERENCED_PARAMETER(Queue);
    NTSTATUS status = STATUS_SUCCESS;
    size_t info = 0;

    KdPrint(("MyKmfd: EvtIoDeviceControl code=0x%X in=%Iu out=%Iu\n", IoControlCode, InputBufferLength, OutputBufferLength));

    switch (IoControlCode) {
    case IOCTL_MY_SIMPLE: {
        const char msg[] = "IOCTL response from kernel";
        size_t toCopy = min(OutputBufferLength, sizeof(msg));
        void* outBuf = NULL;
        size_t outLen = 0;
        status = WdfRequestRetrieveOutputBuffer(Request, toCopy, &outBuf, &outLen);
        if (NT_SUCCESS(status)) {
            RtlCopyMemory(outBuf, msg, toCopy);
            info = toCopy;
        }
        break;
    }
    default:
        status = STATUS_INVALID_DEVICE_REQUEST;
        break;
    }

    WdfRequestCompleteWithInformation(Request, status, info);
}
