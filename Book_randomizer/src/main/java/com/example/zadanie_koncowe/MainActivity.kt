package com.example.zadanie_koncowe
import android.icu.util.Calendar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zadanie_koncowe.ui.theme.Zadanie_koncoweTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.squareup.picasso.Picasso
import androidx.compose.ui.unit.sp
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.Context
import androidx.compose.foundation.lazy.grid.items
import kotlinx.coroutines.flow.asStateFlow


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Zadanie_koncoweTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            painter = painterResource(R.drawable.background_app),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Column(modifier = Modifier.padding(innerPadding)) {
                            val viewModel = BookViewModel()
                            BookScreen(viewModel = viewModel, context = LocalContext.current)
                        }
                    }
                }
            }
        }
    }
}

data class BookInfo(
    @SerializedName("title") val title:String,
    @SerializedName("author_name") val authors: List<String>,
    @SerializedName("cover_i") val covers: String
)
data class BookResponse(

    @SerializedName("works")val info: List<BookInfo>


)

interface BookAppApiService{
    @GET("trending/now.json")
    suspend fun getBooks(

    ):BookResponse

}




object RetrofitInstance{
    //OL45804W.json
    //6498519-M.jpg
    private const val BASE_URL2="https://openlibrary.org/"
    private const val COVER_URL="https://covers.openlibrary.org/b/id/"
    val api:BookAppApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL2)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BookAppApiService::class.java)

    }
    fun getCoverUrl(coverId: String): String {
        return "$COVER_URL$coverId-L.jpg"
    }

}
data class Book(
    @SerializedName("id") var id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("author") var authors: String = "",
    @SerializedName("cover") val cover: String = "",
    @SerializedName("date") val date: Long = Date().time

)

class BookRepository {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("books")
    suspend fun addBook(book: Book) {
        val document = collection.document()
        val bookWithTitle = book.copy(title = book.title)


        document.set(bookWithTitle).await()
    }

    suspend fun getBooks(): List<Book> {
        val snapshot = collection.get().await()
        return snapshot.documents.mapNotNull {
            it.toObject(Book::class.java)
        }
    }
    suspend fun deleteBook(book: Book) {
        collection.document(book.title).delete().await()
    }
}

class BookViewModel: ViewModel() {
    private val _screenCount=MutableStateFlow(0)
    val screenCount=_screenCount.asStateFlow()
    fun updateScreenCount(count:Int){
        _screenCount.value=count
    }
    private val repository = BookRepository()
    private val _bookList = MutableStateFlow<List<Book>>(emptyList())
    val bookList: StateFlow<List<Book>> = _bookList
    fun getBooks() {
        viewModelScope.launch {
            _bookList.value = repository.getBooks()
        }
    }
    fun editBook(book: Book) {
        viewModelScope.launch {
            repository.deleteBook(book)
            repository.addBook(book)
            getBooks()
        }
    }

    fun addBookFromApi( context: Context) {
        viewModelScope.launch {
            val currentDate= Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)

            }.time
            val booksToday= bookList.value.filter {
                val bookDate= Date(it.date)
                bookDate.after(currentDate)
            }
            if (booksToday.size >= 5){
                Toast.makeText(context, "Maksymalna liczba książek na dziś", Toast.LENGTH_SHORT).show()
                return@launch
            }
           // try {
                val response = RetrofitInstance.api.getBooks()


                val book = Book(
                    title = response.info[0].title,
                    authors = response.info[0].authors.toString(),
                    cover = RetrofitInstance.getCoverUrl(response.info[0].covers)
                )
                repository.addBook(book)
                getBooks()
           //
        }


    }



}
@Composable
fun BookScreen(viewModel: BookViewModel,context: Context, modifier: Modifier = Modifier) {
    val bookList by viewModel.bookList.collectAsState(initial = emptyList())
    val screenCount by viewModel.screenCount.collectAsState()
    LaunchedEffect(Unit) {
        viewModel.getBooks()
    }
    if (screenCount == 0) {
        Box(modifier = modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = CenterHorizontally,


                ) {

                Button(
                    onClick = {
                        viewModel.addBookFromApi( context)
                    }, colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6200EE),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(8.dp)
                        .height(50.dp)
                        .fillMaxWidth()
                        .shadow(4.dp, shape = RoundedCornerShape(12.dp))
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Znajdź losową książkę",
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )

                }
                Button(onClick = { viewModel.updateScreenCount(1) }) {
                    Text(text = "Ekran przeszukiwania książek", color = Color.Black)
                }
                BookGrid(books = bookList)
            }
        }


    }
    else{
        NewScreen(viewModel)
    }
}
fun groupByBookDate(books: List<Book>): Map<String, List<Book>> {
    val dateFormatter = SimpleDateFormat("yyyy MMM dd HH:mm", Locale.getDefault())
    return books.sortedByDescending { it.date }.groupBy { book ->
        dateFormatter.format(Date(book.date))
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGrid(books: List<Book>) {
    val groupedBooks = groupByBookDate(books)
    LazyVerticalGrid(
        columns = GridCells.Fixed(1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp)
    ) {
        groupedBooks.forEach { (date, books) ->
            items(books.size) { index ->
                val book = books[index]
                BookItem(
                    book = book,
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                )
            }
        }
    }
}
@Composable
fun BookItem(book: Book, modifier: Modifier) {
    val dateFormat = SimpleDateFormat("yyyy MMM dd HH:mm", Locale.getDefault())
    Column(
        modifier = Modifier

            .padding(16.dp)
            .fillMaxWidth()
            .background(Color(0xFF1A237E), shape = RoundedCornerShape(12.dp))
            .shadow(1.dp, shape=RoundedCornerShape(12.dp))
            .padding(16.dp)

        ,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                }
            },
            modifier = Modifier
                .shadow(4.dp, shape = MaterialTheme.shapes.small, clip = true, spotColor = Color.Black)

        ) { imageView ->
            Picasso.get()
                .load(book.cover)
                .into(imageView)
        }

        Spacer(modifier = Modifier.width(16.dp).padding(bottom = 16.dp))


        Text(text = "Tytuł: ${book.title}", color = Color.White, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Autor: ${book.authors}", color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Data dodania: ${dateFormat.format(Date(book.date))}", color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))


    }
}
@Composable
fun BookItem2(book: Book, modifier: Modifier) {
    val dateFormat = SimpleDateFormat("yyyy MMM dd HH:mm", Locale.getDefault())
    Column(
        modifier = Modifier

            .padding(16.dp)
            .fillMaxWidth()
            .background(Color(0xFF1A237E), shape = RoundedCornerShape(12.dp))
            .shadow(1.dp, shape=RoundedCornerShape(12.dp))
            .padding(16.dp)
            .height(420.dp)

        ,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                }
            },
            modifier = Modifier
                .shadow(4.dp, shape = MaterialTheme.shapes.small, clip = true, spotColor = Color.Black)

        ) { imageView ->
            Picasso.get()
                .load(book.cover)
                .into(imageView)
        }

        Spacer(modifier = Modifier.width(16.dp).padding(bottom = 16.dp))


        Text(text = "Tytuł: ${book.title}", color = Color.White, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold), )
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Autor: ${book.authors}", color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Data dodania: ${dateFormat.format(Date(book.date))}", color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))


    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewScreen(viewModel: BookViewModel){

        Button(onClick = { viewModel.updateScreenCount(0) }) {
            Text(text = "Powrót do ekranu głównego", color = Color.Black)
        }

    val modifier=Modifier
    val searchQuery = remember { mutableStateOf("") }
    val bookList by viewModel.bookList.collectAsState(initial = emptyList())
    val filteredBooks = bookList.filter { it.title.contains(searchQuery.value, ignoreCase = true) }
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = CenterHorizontally,


            ) {
            TextField(
                value = searchQuery.value,
                onValueChange = { searchQuery.value = it },
                label = { Text("Znajdź po nazwie") },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.textFieldColors(
                    focusedIndicatorColor = Color.Blue,
                    focusedLabelColor = Color.Blue
                )
            )
            Spacer(modifier = Modifier.height(16.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)

            ) {
                items(filteredBooks) { book ->
                    BookItem2(book = book, modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}


