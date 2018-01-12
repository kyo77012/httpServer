package httpServer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

interface HTTP_Status_Code {
  // 2xx Success
  public static final int OK = 200;
  public static final int Partial_Content = 206;
  
  // 3xx Redirection
  public static final int Moved_Permanently = 301;
  public static final int Not_Modified = 304;

  // 4xx Client Error
  public static final int Bad_Request = 400;
  public static final int Forbiden = 403;
  public static final int Not_Found = 404;
  public static final int Method_Not_Allowed = 405;
  public static final int Precondition_Failed = 412;

  // 5xx Server Error
  public static final int HTTP_Version_Not_Supported = 505;
} // interface HTTP_Status_Code

enum HTTP_Request_Methods {
  GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS, CONNECT, PATCH;
} // HTTP_Request

class Config {
  public static int port = 10377;
  public static String docRoot = "./src/web";
  public static String indexFile = "index.html";
  public static String ip = "140.135.51.127";
  public static String USER = "user01";
  public static String PASSWORD = "user01";
} // class Config

class DirInfo {
  public File dir;
  public long lastModified;
  public Vector< FileInfo > files;

  public DirInfo( File dir ) {
    this.dir = dir;
    this.lastModified = dir.lastModified();
    this.files = new Vector< FileInfo >();
  } // DirInfo()
} // class DirInfo

class FileInfo {
  public File file;
  public long lastModified;

  public FileInfo( File file ) {
    this.file = file;
    this.lastModified = file.lastModified();
  } // FileInfo()
} // class FileInfo

interface AllFiles {
  public static Vector< DirInfo > dirs = new Vector< DirInfo >();
} // interface AllFiles

public class HttpServer {
  private static ServerSocket server;
  private static Socket newClient;

  public static void main( String[] args ) {
    try {
      System.out.println( "文件根目錄: " + Config.docRoot + "\n索引文件: " + Config.indexFile + "\n連接埠: " + Config.port );
      new FileManager();

      ServerSocket server = new ServerSocket( Config.port );
      while ( true ) {
        newClient = server.accept();
        HandleRequest handleRequest = new HandleRequest( newClient );
        Thread newThread = new Thread( handleRequest );
        handleRequest.SetThreadID( newThread.getId() );
        newThread.start();
        System.out.println( "threadID: " + newThread.getId() + " 客戶端連線: " + newClient.getInetAddress() );
      } // while
    } // try
    catch ( Exception e ) {
      System.out.println( e.getMessage() );
      e.printStackTrace();
    } // catch
  } // main()
} // class HttpServer

class FileManager implements AllFiles {
  public FileManager() throws IOException {
    FileManager.UpdateFiles();
    Runnable updateFileTask = new Runnable() {
      @Override
      public void run() {
        try {
          while ( true ) {
            if ( !FileManager.IsModified() ) {
              Thread.sleep( 3000 );
              continue;
            } // if
            
            FileManager.UpdateFiles();
          } // while
        } // try 
        catch ( Exception e ) {
          System.out.println( e.getMessage() );
          e.printStackTrace();
        } // catch
      } // run()
    };
    
    Thread updateFileThread = new Thread( updateFileTask );
    updateFileThread.start();
  } // FileManager()

  private static void GetWebFiles( String filePath, DirInfo curDirInfo ) throws IOException {
    File curFile = new File( filePath );
    if ( curFile.isDirectory() ) {
      System.out.println( "目錄:" + curFile );
      DirInfo nextDirInfo = new DirInfo( curFile );
      String nextDirFullPath = curFile.getCanonicalPath();
      AllFiles.dirs.add( nextDirInfo );
      String[] inNextDir = curFile.list();
      for ( int i = 0 ; i < inNextDir.length ; i++ )
        GetWebFiles( nextDirFullPath + "\\" + inNextDir[i], nextDirInfo );
    } // if
    else {
      System.out.println( "檔案:" + curFile );
      curDirInfo.files.add( new FileInfo( curFile ) );
    } // else
  } // GetFiles()

  private static void CheckAllFiles() throws IOException {
    DirInfo curDirInfo;
    FileInfo curFileInfo;
    for ( int i = 0 ; i < AllFiles.dirs.size() ; i++ ) {
      curDirInfo = AllFiles.dirs.get( i );
      for ( int j = 0 ; j < curDirInfo.files.size() ; j++ ) {
        curFileInfo = curDirInfo.files.get( j );
        System.out.println( curFileInfo.file.getCanonicalPath() );
      } // for
    } // for
  } // CheckAllFiles()

  public static FileInfo GetFile( String fileFullPath ) throws IOException {
    DirInfo curDirInfo;
    FileInfo curFileInfo;
    for ( int i = 0 ; i < AllFiles.dirs.size() ; i++ ) {
      curDirInfo = AllFiles.dirs.get( i );
      if ( curDirInfo.dir.getCanonicalPath().equals( fileFullPath ) )
        return new FileInfo( curDirInfo.dir );
      for ( int j = 0 ; j < curDirInfo.files.size() ; j++ ) {
        curFileInfo = curDirInfo.files.get( j );
        if ( curFileInfo.file.getCanonicalPath().equals( fileFullPath ) )
          return curFileInfo;
      } // for
    } // for
    return null;
  } // GetFile()
  
  private static void UpdateFiles() throws IOException {
    synchronized ( AllFiles.dirs ) {
      for ( int i = 0 ; i < AllFiles.dirs.size() ; i++ )
        AllFiles.dirs.get( i ).files.removeAllElements();
      AllFiles.dirs.removeAllElements();
      
      File rootFile = new File( Config.docRoot );
      if ( !rootFile.exists() || !rootFile.isDirectory() ) {
        System.out.println( "找不到root目錄" );
        return;
      } // if
      DirInfo rootDirInfo = new DirInfo( rootFile );
      String rootDirFullPath = rootFile.getCanonicalPath();
      Config.docRoot = rootDirFullPath;
      AllFiles.dirs.add( rootDirInfo );
      String[] inRootDir = rootFile.list();
      for ( int i = 0 ; i < inRootDir.length ; i++ ) {
        GetWebFiles( rootDirFullPath + "\\" + inRootDir[i], rootDirInfo );
      } // for
      CheckAllFiles();
    } // synchronized
  } // UpdateFiles()
  
  private static boolean IsModified() {
    DirInfo curDirInfo;
    FileInfo curFileInfo;
    for ( int i = 0 ; i < AllFiles.dirs.size() ; i++ ) {
      curDirInfo = AllFiles.dirs.get( i );
      if ( curDirInfo.lastModified != curDirInfo.dir.lastModified() ) return true;
      for ( int j = 0 ; j < curDirInfo.files.size() ; j++ ) {
        curFileInfo = curDirInfo.files.get( j );
        if ( curFileInfo.lastModified != curFileInfo.file.lastModified() ) return true;
      } // for
    } // for
    return false;
  } // IsModified()
} // class FileManager

class HandleRequest implements Runnable {
  private long threadID = -1;
  private final Socket client;
  private DataInputStream input;
  private PrintStream output;

  private String string_requestMethod = "";
  private HTTP_Request_Methods request_Method;
  private String requestFileName = "";
  private String args;
  private String httpVersion = "";

  private String cookie;
  private String userNm = new String( "anonymous" ),
                 userPwd = new String( "" ),
                 action = new String( "" );
  
  
  // condition GET //
  private String If_Modified_Since;
  private String If_Unmodified_Since;
  private String If_Match;
  private String If_Non_Match;
  private String If_Range;
  
  private String Range;
  private int Range_begin = -1, Range_end = -1;
  // condition GET //
  
  
  
  private int content_length = 0;
  private String content_type;
  private String content_type_boundary;
  private String data;
  
  private byte[] binaryData;
  
  boolean needReDirect = false;
  private static DateFormat gmtTimeFormat = SetDateFormat();
  
  private static DateFormat SetDateFormat() {
    DateFormat gmtTime = new SimpleDateFormat( "EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH );
    gmtTime.setTimeZone( TimeZone.getTimeZone( "GMT" ) );
    return gmtTime;
  } // SetDateFormat()
  
  public void SetThreadID( long threadID ) {
    this.threadID = threadID;
  } // SetThreadID()

  public HandleRequest( final Socket newClient ) {
    this.client = newClient;
    try {
      client.setSoTimeout( 10000 );
      this.input = new DataInputStream( client.getInputStream() );
      this.output = new PrintStream( newClient.getOutputStream() );
    } // try
    catch ( IOException e ) {
      System.out.println( e.getMessage() );
      e.printStackTrace();
    } // catch
  } // HandleReuqest()

  @Override
  public void run() {
    while ( threadID == -1 ) ;
    try {
      ReadInput();
      ParseInput();
      CheckInput();
      Reply();
    } // try
    catch ( HTTP_Error_Exception e ) {
      HandleError( e.getMessage() );
    } // catch
    catch ( Throwable e ) {
      System.out.println( e.getMessage() );
      e.printStackTrace();
    } // catch
    finally {
      output.close();
      try {
        client.close();
      } // try
      catch ( IOException e ) {
        System.out.println( e.getMessage() );
        e.printStackTrace();
      } // catch
    } // finally
    System.out.println( "thread: " + threadID + " over" );
  } // run()

  void ReadInput() throws IOException {
    String[] token;
    if ( input.available() > 0 ) {
      token = ( input.readLine() ).split( " " );
      if ( token.length == 3 ) {
        string_requestMethod = token[0];
        requestFileName = token[1];
        httpVersion = token[2];
      } // if
    } // if

    while ( input.available() > 0 ) {
      token = input.readLine().split( "[ ]" );
      if ( string_requestMethod.equals( "PUT" ) ) {
        for ( String s : token ) System.out.print( s + " " );
        System.out.println();
      } // if
      if ( token[0].equals( "" ) ) {
        System.out.println( "thread: " + threadID + " break" );
        break;
      } // if
      else if ( token[0].equals( "Cookie:" ) ) {
        if ( cookie == null ) cookie = new String( "" );
        for ( int i = 1 ; i < token.length ; i++ ) {
          System.out.println( token[i] );
          if ( token[i].equals( "" ) ) continue;
          else cookie += token[i] + " ";
        } // for
      } // else if
      else if ( token[0].equals( "Content-Length:" ) ) {
        for ( int i = 1 ; i < token.length ; i++ ) {
          if ( token[i].equals( "" ) ) continue;
          else {
            content_length = Integer.parseInt( token[i] );
            break;
          } // else
        } // for
      } // else if
      else if ( token[0].equals( "Content-Type:" ) ) {
        for ( int i = 1 ; i < token.length ; i++ ) {
          if ( token[i].equals( "" ) ) continue;
          else if ( token[i].startsWith( "multipart/form-data" ) )
            content_type = "multipart/form-data";
          else if ( token[i].startsWith( "text/plain" ) )
            content_type = "text/plain";
          else if ( token[i].startsWith( "image/jpeg" ) )
            content_type = "image/jpeg";
          else if ( token[i].startsWith( "application/x-www-form-urlencoded" ) )
            content_type = "application/x-www-form-urlencoded";
          else if ( token[i].startsWith( "boundary=" ) )
            content_type_boundary = "--" + ( token[i].split( "=" ) )[1];
        } // for
      } // else if
      
      
      // condition GET //
      else if ( token[0].equals( "If-Modified-Since:" ) ) {
        If_Modified_Since = new String( "" );
        for ( int i = 1 ; i < token.length ; i++ ) {
          If_Modified_Since += token[i];
          if ( i < token.length - 1 ) If_Modified_Since += " ";
        } // for
      } // else if
      else if ( token[0].equals( "If-Unmodified-Since:" ) ) {
        If_Unmodified_Since = new String( "" );
        for ( int i = 1 ; i < token.length ; i++ ) {
          If_Unmodified_Since += token[i];
          if ( i < token.length - 1 ) If_Unmodified_Since += " ";
        } // for
      } // else if
      else if ( token[0].equals( "If-Match:" ) ) {
        If_Match = new String( "" );
        for ( int i = 1 ; i < token.length ; i++ ) If_Match += token[i];
      } // else if
      else if ( token[0].equals( "If-Non-Match:" ) ) {
        If_Non_Match = new String( "" );
        for ( int i = 1 ; i < token.length ; i++ ) If_Non_Match += token[i];
      } // else if
      else if ( token[0].equals( "If-Range:" ) ) {
        If_Range = new String( "" );
        for ( int i = 1 ; i < token.length ; i++ ) {
          If_Range += token[i];
          if ( i < token.length - 1 ) If_Range += " ";
        } // for
      } // else if

      
      else if ( token[0].equals( "Range:" ) ) {
        Range = new String( "" );
        for ( int i = 1 ; i < token.length ; i++ ) Range += token[i];
        String[] Range_token = Range.split( "[-]" );
        Range_begin = Integer.parseInt( Range_token[0] );
        Range_end = Integer.parseInt( Range_token[1] );
      } // else if
      // condition GET//
      
      
    } // while
    
    if ( content_length > 0 ) {
      output.println( httpVersion + " 100 Continue" );
      output.println();
      System.out.println( "thread: " + threadID + " Content_length: " + content_length );

      
      if ( content_type.equals( "application/x-www-form-urlencoded" ) ||
           content_type.equals( "text/plain" ) ||
           content_type.equals( "image/jpeg" ) ) {
        binaryData = new byte[ content_length ];
        for ( int i = 0 ; i < content_length ; i++ )
          binaryData[i] = ( byte ) input.read();
        
        data = new String( binaryData, "utf-8" );
      } // if
      else if ( content_type.equals( "multipart/form-data" ) ) {
        data = new String( "" );
        data += input.readLine() + "\r\n";
        if ( data.startsWith( content_type_boundary ) ) {
          data += input.readLine() + "\r\n";
          data += input.readLine() + "\r\n";
          data += input.readLine() + "\r\n";
          binaryData = new byte[ content_length - data.getBytes( "utf-8" ).length - content_type_boundary.length() - ( new String( "\r\n--\r\n" ) ).getBytes( "utf-8" ).length ];
                                 // 多出6個utf-8字元: 最後面"--\r\n"跟content_type_boundary前的"\r\n"
          for ( int i = 0 ; i < binaryData.length ; i++ )
            binaryData[i] = ( byte ) input.read();
        } // if
      } // else if
      
      System.out.println( "thread: " + threadID + " length: " + binaryData.length );
      // System.out.println( "thread: " + threadID + " Data: " );
      // System.out.println( data );
    } // if
  } // ReadInput()

  void CheckInput() {
    if ( string_requestMethod != null ) System.out.println( "threadID: " + threadID + " " + "requestMethod: " + string_requestMethod );
    else System.out.println( "threadID: " + threadID + " " + "requestMethod: " + "null" );

    if ( requestFileName != null ) System.out.println( "threadID: " + threadID + " " + "requestFile: " + requestFileName );
    else System.out.println( "threadID: " + threadID + " " + "requestFile: " + "null" );

    if ( httpVersion != null ) System.out.println( "threadID: " + threadID + " " + "httpVersion: " + httpVersion );
    else System.out.println( "threadID: " + threadID + " " + "httpVersion: " + "null" );

    if ( cookie != null ) System.out.println( "threadID: " + threadID + " " + "Cookie: " + cookie );
    else System.out.println( "threadID: " + threadID + " " + "Cookie: " + "null" );

    System.out.println( "threadID: " + threadID + " " + "Content_length: " + content_length );

    if ( content_type != null ) System.out.println( "threadID: " + threadID + " " + "content_type: " + content_type );
    else System.out.println( "threadID: " + threadID + " " + "content_type:" + "null" );
    
    if ( content_type_boundary != null ) System.out.println( "threadID: " + threadID + " " + "boundary: " + content_type_boundary );
    else System.out.println( "threadID: " + threadID + " " + "boundary:" + "null" );
    
    
    
    // condition GET //
    if ( If_Modified_Since != null ) System.out.println( "If_Modified_Since: " + If_Modified_Since );
    else System.out.println( "If_Modified_Since: " + "null" );
    
    if ( If_Unmodified_Since != null ) System.out.println( "If_Unmodified_Since: " + If_Unmodified_Since );
    else System.out.println( "If_Unmodified_Since: " + "null" );
    
    if ( If_Match != null ) System.out.println( "If_Match: " + If_Match );
    else System.out.println( "If_Match: " + "null" );
    
    if ( If_Non_Match != null ) System.out.println( "If_Non_Match: " + If_Non_Match );
    else System.out.println( "If_Non_Match: " + "null" );
    
    if ( If_Range != null ) System.out.println( "If_Range: " + If_Range );
    else System.out.println( "If_Range: " + "null" );
    
    
    if ( Range != null ) System.out.println( "Range: " + Range_begin + " " + Range_end );
    // condition GET //
    
    
    if ( data != null ) {
      System.out.println( "threadID: " + threadID + " " + "Data: " );
      System.out.println( data );
    } // if
    else System.out.println( "threadID: " + threadID + " " + "Data: " + "null" );
  } // CheckInput()

  void ParseInput() throws Throwable {
    CheckRequest();
    CheckHttpVersion();
    if ( requestFileName.contains( "?" ) ) {
      String[] tempRequestFileName = requestFileName.split( "[?]" );
      requestFileName = tempRequestFileName[0];
      if ( tempRequestFileName.length == 2 )
        args = tempRequestFileName[1];
      else throw new Bad_Request_Exception();
    } // if
    requestFileName = requestFileName.replace( '/', '\\' );
    if ( requestFileName.endsWith( "\\" ) ) {
      requestFileName += Config.indexFile;
      needReDirect = true;
    } // if
    else requestFileName = Config.docRoot + requestFileName;
    
    if ( cookie != null ) {
      String[] cookieSplit = cookie.split( "[ ;]" );
      String temp;
      for ( int i = 0 ; i < cookieSplit.length ; i++ ) {
        temp = cookieSplit[i];
        if ( temp.startsWith( "userNm=" ) ) {
          userNm = temp.split( "[=]" )[1];
        } // if
      } // for
    } // if
  } // ParseInput()

  void CheckRequest() throws Throwable {
    if ( string_requestMethod.equals( "GET" ) )
      request_Method = HTTP_Request_Methods.GET;
    else if ( string_requestMethod.equals( "HEAD" ) )
      request_Method = HTTP_Request_Methods.HEAD;
    else if ( string_requestMethod.equals( "POST" ) )
      request_Method = HTTP_Request_Methods.POST;
    else if ( string_requestMethod.equals( "PUT" ) )
      request_Method = HTTP_Request_Methods.PUT;
    else if ( string_requestMethod.equals( "DELETE" ) )
      request_Method = HTTP_Request_Methods.DELETE;
    else if ( string_requestMethod.equals( "TRACE" ) )
      request_Method = HTTP_Request_Methods.TRACE;
    else if ( string_requestMethod.equals( "OPTIONS" ) )
      request_Method = HTTP_Request_Methods.OPTIONS;
    else if ( string_requestMethod.equals( "CONNECT" ) )
      request_Method = HTTP_Request_Methods.CONNECT;
    else if ( string_requestMethod.equals( "PATCH" ) )
      request_Method = HTTP_Request_Methods.PATCH;
    else throw new Bad_Request_Exception();
  } // CheckRequest()

  void CheckHttpVersion() throws Throwable {
    if ( httpVersion.equals( "HTTP/1.1" ) ) {
      if ( request_Method == HTTP_Request_Methods.TRACE
          || request_Method == HTTP_Request_Methods.OPTIONS
          || request_Method == HTTP_Request_Methods.CONNECT
          || request_Method == HTTP_Request_Methods.PATCH )
        throw new Method_Not_Allowed_Exception();
    } // if
    else if ( httpVersion.equals( "HTTP/1.0" ) ) {
      if ( request_Method == HTTP_Request_Methods.PUT
          || request_Method == HTTP_Request_Methods.DELETE
          || request_Method == HTTP_Request_Methods.TRACE
          || request_Method == HTTP_Request_Methods.OPTIONS
          || request_Method == HTTP_Request_Methods.CONNECT
          || request_Method == HTTP_Request_Methods.PATCH )
        throw new Bad_Request_Exception();
    } // else if
    else throw new HTTP_Version_Not_Supported_Exception();
  } // CheckHttpVersion()

  void Reply() throws Exception {
    if ( needReDirect ) Redirect();
    else if ( request_Method == HTTP_Request_Methods.GET ) DoGET();
    else if ( request_Method == HTTP_Request_Methods.HEAD ) DoHEAD();
    else if ( request_Method == HTTP_Request_Methods.POST ) DoPOST();
    else if ( request_Method == HTTP_Request_Methods.PUT ) DoPUT();
    else if ( request_Method == HTTP_Request_Methods.DELETE ) DoDELETE();
  } // Reply()

  void DoGET() throws Exception {
    System.out.println( "threadID: " + threadID + " GET: " + requestFileName );
    FileInfo requestFileInfo = FileManager.GetFile( requestFileName );
    if ( requestFileInfo == null ) throw new Not_Found_Exception();
    
    String Last_Modified_string = gmtTimeFormat.format( requestFileInfo.lastModified );
    Date Last_Modified_date = gmtTimeFormat.parse( Last_Modified_string );
    
    boolean need_retransmit = true; 
    if ( If_Modified_Since != null ) {
      try {
        Date If_Modified_Since_date = gmtTimeFormat.parse( If_Modified_Since );
        if ( If_Modified_Since_date.after( Last_Modified_date ) ||
             If_Modified_Since_date.equals( Last_Modified_date ) ) need_retransmit = false;
      } // try
      catch( ParseException parseException ) {
      } // catch
    } // if
    
    if ( If_Unmodified_Since != null ) {
      try {
        Date If_Unmodified_Since_date = gmtTimeFormat.parse( If_Unmodified_Since );
        if ( If_Unmodified_Since_date.before( Last_Modified_date ) )
          throw new Precondition_Failed_Exception();
        else if ( If_Unmodified_Since_date.equals( Last_Modified_date ) ) need_retransmit = false;
      } // try
      catch( ParseException parseException ) {
      } // catch
    } // if
    
    
    String ETag = "\"" + requestFileInfo.lastModified + "\"";
    if ( If_Match != null ) {
      if ( !If_Match.equals( ETag ) && !If_Match.equals( "*" ) )
        throw new Precondition_Failed_Exception();
    } // if
    
    if ( If_Non_Match != null ) {
      if ( If_Non_Match.equals( ETag ) || If_Non_Match.equals( "*" ) ) need_retransmit = false;
    } // if
    
    boolean isPartial_Content = false;
    if ( If_Range != null ) {
      Date If_Range_date = null;
      boolean isDate = false;
      try {
        If_Range_date = gmtTimeFormat.parse( If_Range );
        isDate = true;
      } // try
      catch ( ParseException parseException ) {
      } // catch
      
      if ( isDate ) {
        if ( If_Range_date.equals( Last_Modified_date ) ) {
          if ( Range != null ) {
            need_retransmit = false;
            isPartial_Content = true;
          } // if
        } // if
      } // if
      else {
        if ( If_Range.equals( ETag ) ) {
          if ( Range != null ) {
            need_retransmit = false;
            isPartial_Content = true;
          } // if
        } // if
      } // else
    } // if
    
    if ( need_retransmit ) output.println( httpVersion + " " + HTTP_Status_Code.OK + " OK" );
    else if ( isPartial_Content ) output.println( httpVersion + " " + HTTP_Status_Code.Partial_Content + " Partial Content" );
    else output.println( httpVersion + " " + HTTP_Status_Code.Not_Modified + " Not Modified" );
    
    output.println( "Connection: close" );
    output.println( "Date: " + gmtTimeFormat.format( new Date() ) );
    output.println( "Server: CreepyHttpServer/0.0.1" );
    output.println( "Last-Modified: " + Last_Modified_string );
    
    if ( isPartial_Content ) {
      output.println( "Content-Range: bytes " + Range + "/" + requestFileInfo.file.length() );
      output.println( "Content-Length: " + ( Range_end - Range_begin ) );
    } // if
    else output.println( "Content-Length: " + requestFileInfo.file.length() );
    
    output.println( "Content-Type: " + "text/html" + ";charset=utf-8" );
    output.println( "ETag: " + ETag );
    if ( cookie == null ) output.println( "Set-Cookie: userNm=" + userNm );
    output.println();
    
    if ( need_retransmit ) {
      FileInputStream fileInputStream = new FileInputStream( requestFileInfo.file );
      byte[] requestFileData = new byte[( int ) requestFileInfo.file.length()];
      fileInputStream.read( requestFileData );
      fileInputStream.close();
      output.write( requestFileData );
    } // if
    else if ( isPartial_Content ) {
      FileInputStream fileInputStream = new FileInputStream( requestFileInfo.file );
      byte[] requestFileData = new byte[( int ) requestFileInfo.file.length()];
      fileInputStream.read( requestFileData );
      fileInputStream.close();
      output.write( requestFileData, Range_begin, Range_end - Range_begin );
    } // else if
  } // DoGET()

  void DoHEAD() throws Exception {
    System.out.println( "threadID: " + threadID + " HEAD: " + requestFileName );
    FileInfo requestFileInfo = FileManager.GetFile( requestFileName );
    if ( requestFileInfo == null ) throw new Not_Found_Exception();

    output.println( httpVersion + " 200 OK" );
    output.println( "Connection: close" );
    output.println( "Date: " + gmtTimeFormat.format( new Date() ) );
    output.println( "Server: CreepyHttpServer/0.0.1" );
    output.println( "Last-Modified: " + gmtTimeFormat.format( requestFileInfo.lastModified ) );
    output.println( "Content-Length: " + requestFileInfo.file.length() );
    output.println( "Content-Type: " + "text/html" + ";charset=utf-8" );
    output.println( "ETag: " + '"' + requestFileInfo.lastModified + '"' );
    if ( cookie == null ) output.println( "Set-Cookie: userNm=" + userNm );
    output.println();
  } // DoHEAD()

  void DoPOST() throws Exception {
    System.out.println( "threadID: " + threadID + " POST: " + requestFileName );
    if ( data != null ) {
      String[] dataSplit = data.split( "[&]" );
      String temp;
      for ( int i = 0 ; i < dataSplit.length ; i++ ) {
        temp = dataSplit[i];
        if ( temp.startsWith( "userNm=" ) ) {
          userNm = temp.split( "[=]" )[1];
        } // if
        else if ( temp.startsWith( "userPwd=" ) ) {
          userPwd = temp.split( "[=]" )[1];
        } // else if
        else if ( temp.startsWith( "action=" ) ) {
          action = temp.split( "[=]" )[1];
        } // else if
      } // for
    } // if
    
    if ( action.equals( "logout" ) ) userNm = "anonymous";
    else if ( action.equals( "login" ) ) {
      System.out.println( userNm + " " + userPwd + " " + action );
      if ( !userNm.equals( Config.USER ) || !userPwd.equals( Config.PASSWORD ) )
        userNm = "anonymous";
    } // else if
    else throw new Bad_Request_Exception();
    
    
    FileInfo requestFileInfo = FileManager.GetFile( requestFileName );
    if ( requestFileInfo == null ) throw new Not_Found_Exception();
    
    output.println( httpVersion + " 200 OK" );
    output.println( "Connection: close" );
    output.println( "Date: " + gmtTimeFormat.format( new Date() ) );
    output.println( "Server: CreepyHttpServer/0.0.1" );
    output.println( "Last-Modified: " + gmtTimeFormat.format( requestFileInfo.lastModified ) );
    output.println( "Content-Length: " + requestFileInfo.file.length() );
    output.println( "Content-Type: " + "text/html" + ";charset=utf-8" );
    output.println( "ETag: " + '"' + requestFileInfo.lastModified + '"' );
    output.println( "Set-Cookie: userNm=" + userNm );
    output.println();
    
    FileInputStream fileInputStream = new FileInputStream( requestFileInfo.file );
    byte[] requestFileData = new byte[( int ) requestFileInfo.file.length()];
    fileInputStream.read( requestFileData );
    fileInputStream.close();
    output.write( requestFileData );
  } // DoPOST()

  void DoPUT() throws Exception {
    System.out.println( "threadID: " + threadID + " PUT: " + requestFileName );
    
    if ( !userNm.equals( Config.USER ) ) throw new Forbidden_Exception();
    
    FileInfo requestFileInfo = FileManager.GetFile( requestFileName );
    if ( requestFileInfo == null ) {
      File newFile = new File( requestFileName );
      File tempFile;
      String tempFileName = new String( "" );
      System.out.println( requestFileName );
      String[] token = requestFileName.split( "[\\\\]" );
      for ( int i = 0 ; i < token.length - 1 ; i++ ) {
        tempFileName += token[i] + "\\";
        tempFile = new File( tempFileName );
        if ( tempFile.exists() ) continue;
        else tempFile.mkdir();
      } // for
      
      newFile.createNewFile();
      PrintStream ps = new PrintStream( newFile );
      ps.write( binaryData );
      ps.close();
    } // if
    else {
      PrintStream ps = new PrintStream( requestFileInfo.file );
      ps.write( binaryData );
      ps.close();
    } // else
    
    System.out.println( "done!" );
    output.println( httpVersion + " 200 OK" );
    output.println( "Date: " + gmtTimeFormat.format( new Date() ) );
    output.println( "Server: CreepyHttpServer/0.0.1" );
    output.println( "Content-Type: " + "text/html" + ";charset=utf-8" );
    if ( cookie == null ) output.println( "Set-Cookie: userNm=" + userNm );
    output.println();
  } // DoPUT()

  void DoDELETE() throws Exception {
    System.out.println( "threadID: " + threadID + " DELETE: " + requestFileName );
    
    if ( !userNm.equals( Config.USER ) ) throw new Forbidden_Exception();
    
    FileInfo requestFileInfo = FileManager.GetFile( requestFileName );
    if ( requestFileInfo == null ) throw new Not_Found_Exception();
    
    requestFileInfo.file.delete();
    
    output.println( httpVersion + " 200 OK" );
    output.println( "Connection: close" );
    output.println( "Date: " + gmtTimeFormat.format( new Date() ) );
    output.println( "Server: CreepyHttpServer/0.0.1" );
    output.println( "Last-Modified: " + gmtTimeFormat.format( requestFileInfo.lastModified ) );
    output.println( "Content-Length: " + requestFileInfo.file.length() );
    output.println( "Content-Type: " + "text/html" + ";charset=utf-8" );
    output.println( "ETag: " + '"' + requestFileInfo.lastModified + '"' );
    if ( cookie == null ) output.println( "Set-Cookie: userNm=" + userNm );
    output.println();
  } // DoDELETE()
  
  void Redirect() {
    System.out.println( "threadID: " + threadID + " Moved Permanently: " + requestFileName );
    output.println( "HTTP/" + httpVersion + " 301 Moved Permanently" );
    output.println( "Connection: close" );
    output.println( "Date: " + gmtTimeFormat.format( new Date() ) );
    output.println( "Server: CreepyHttpServer/0.0.1" );
    output.println( "Location: " + "http://" + Config.ip + ":" + Config.port + "/aaa" );
    output.println( "Content-Type: " + "text/html" + ";charset=utf-8" );
    output.println();
  } // Redirect()
  
  void HandleError( String msg ) {
    output.println( httpVersion + " " + msg );
    output.println( "Connection: close" );
    output.println( "Date: " + gmtTimeFormat.format( new Date() ) );
    output.println( "Server: CreepyHttpServer/0.0.1" );
    output.println( "Content-Type: " + "text/html" + ";charset=utf-8" );
    output.println();
  } // HandleError()
} // class HandleRequest







class HTTP_Error_Exception extends Exception {
  public HTTP_Error_Exception( String msg ) {
    super( msg );
  } // HTTP_Error_Exception()
} // HTTP_Error_Exception





class Client_Error_Exception extends HTTP_Error_Exception {
  public Client_Error_Exception( String msg ) {
    super( msg );
  } // Client_Error_Exception()
} // class Client_Error_Exception

class Bad_Request_Exception extends Client_Error_Exception implements HTTP_Status_Code {
  public Bad_Request_Exception() {
    super( HTTP_Status_Code.Bad_Request + " Bad Request" );
  } // Bad_Request_Exception()
} // class Bad_Request_Exception

class Forbidden_Exception extends Client_Error_Exception implements HTTP_Status_Code {
  public Forbidden_Exception() {
    super( HTTP_Status_Code.Forbiden + " Forbidden" );
  } // Forbidden_Exception()
} // class Forbidden_Exception

class Not_Found_Exception extends Client_Error_Exception implements HTTP_Status_Code {
  public Not_Found_Exception() {
    super( HTTP_Status_Code.Not_Found + " Not Found" );
  } // Not_Found_Exception()
} // class Not_Found_Exception

class Method_Not_Allowed_Exception extends Client_Error_Exception implements HTTP_Status_Code {
  public Method_Not_Allowed_Exception() {
    super( HTTP_Status_Code.Method_Not_Allowed + "  Method Not Allowed" );
  } // Method_Not_Allowed_Exception()
} // class Method_Not_Allowed_Exception

class Precondition_Failed_Exception extends Client_Error_Exception implements HTTP_Status_Code {
  public Precondition_Failed_Exception() {
    super( HTTP_Status_Code.Precondition_Failed + "  Precondition Failed" );
  } // Precondition_Failed_Exception()
} // class Precondition_Failed_Exception




class Server_Error_Exception extends HTTP_Error_Exception {
  public Server_Error_Exception( String msg ) {
    super( msg );
  } // Server_Error_Exception()
} // class Server_Error_Exception

class HTTP_Version_Not_Supported_Exception extends Server_Error_Exception
    implements HTTP_Status_Code {
  public HTTP_Version_Not_Supported_Exception() {
    super( HTTP_Status_Code.HTTP_Version_Not_Supported + " HTTP Version Not Supported" );
  } // HTTP_Version_Not_Supported()
} // class HTTP_Version_Not_Supported